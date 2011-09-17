/*
 * Copyright 2011 - Alistair Rutherford - www.netthreads.co.uk
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 */
package com.netthreads.mavenize;

import com.netthreads.mavenize.model.mapping.Mapping;
import com.netthreads.mavenize.model.mapping.Mappings;
import com.netthreads.mavenize.model.mapping.DependencySource;
import com.netthreads.mavenize.model.mapping.DependencyTarget;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import javax.xml.bind.JAXBException;
import org.apache.commons.collections.Predicate;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.DirectoryWalker;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.WriterFactory;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pommel
 * 
 * Takes mapping file and maps source dependencies to targe ones.
 * 
 * This is a companion tool to "mavenize" which will take existing java projects
 * and convert them into mavenized versions. You can find it here:
 * 
 * http://code.google.com/p/mavenize-tool/
 *
 */
public class Pommel
{
    private static Logger logger = LoggerFactory.getLogger(ProjectFileFinder.class);
    private static final String APP_MESSAGE = "pommel version 1.0.0\nAlistair Rutherford, www.netthreads.co.uk, 2011.\nLicensed under the Apache License, Version 2.0.\n\n";
    private static final String ARGS_MESSAGE = "Arguments: -i<source dir> -m<path to map file>";
    private static final String FILENAME_POM = "pom.xml";
    private static final String ARG_INPUT = "-i";
    private static final String ARG_MAP = "-m";
    private static final String TEXT_WILDCARD = "*";

    /**
     * Arguments: <source dir> <target dir> <project type> [Optional parameter 
     * can be intellij, netbeans]
     * 
     * @param args
     */
    public static void main(String[] args)
    {
        if (args.length > 1)
        {
            String sourcePath = "";
            String mapFilePath = "";

            boolean isInput = false;
            boolean isMap = false;
            for (String arg : args)
            {
                try
                {
                    if (arg.startsWith(ARG_INPUT))
                    {
                        sourcePath = arg.substring(ARG_INPUT.length());
                        isInput = true;
                    }
                    else if (arg.startsWith(ARG_MAP))
                    {
                        mapFilePath = arg.substring(ARG_MAP.length());
                        isMap = true;
                    }
                }
                catch (Exception e)
                {
                    logger.error("Can't process argument, " + arg + ", " + e.getMessage());
                }
            }

            // Execute mapping.
            try
            {
                if (isInput && isMap)
                {
                    Pommel pommel = new Pommel();

                    pommel.process(sourcePath, mapFilePath);
                }
                else
                {
                    System.out.println(APP_MESSAGE + ARGS_MESSAGE);
                }
            }
            catch (PommelException e)
            {
                System.out.println("Application error, " + e.getMessage());
            }
        }
        else
        {
            System.out.println(APP_MESSAGE + ARGS_MESSAGE);
        }
    }

    /**
     * Main process method.
     * 
     * @param sourcePath
     * @param targetPath
     * @throws IOException 
     */
    private void process(String sourcePath, String mapFilePath) throws PommelException
    {
        try
        {
            // Generate records for all source parent, child and target directories.
            List<File> pomFiles = buildPomFilesList(sourcePath);

            logger.info("Pom Files, " + pomFiles.size());

            // Read mappings.
            List<Mapping> mappings = loadDependencyMappings(mapFilePath);

            // Process mappings.
            processDependencyMappings(pomFiles, mappings);
        }
        catch (IOException ex)
        {
            throw new PommelException(ex.getMessage());
        }
    }

    /**
     * Process pom files with defined old and new dependency mappings.
     * 
     * @param pomFiles
     * @param mappings 
     */
    private void processDependencyMappings(List<File> files, List<Mapping> mappings)
    {
        for (File file : files)
        {
            String pomPath = file.getAbsolutePath();

            try
            {
                // Read pom
                Model model = readPom(pomPath);

                List<Dependency> dependencies = model.getDependencies();

                // Save us writing back poms we didn't update.
                boolean sticky = false;

                for (Dependency dependency : dependencies)
                {
                    // Check each dependency
                    Predicate searchPredicate = new DependencyPredicate(dependency.getGroupId(), dependency.getArtifactId(), dependency.getVersion());
                    Mapping target = (Mapping) CollectionUtils.find(mappings, searchPredicate);

                    if (target != null)
                    {
                        logger.debug(dependency.getGroupId() + ", " + dependency.getArtifactId() + " : " + target.getDependencyTarget().getGroupId() + ", " + target.getDependencyTarget().getArtifactId());

                        DependencyTarget dependencyTarget = target.getDependencyTarget();
                        dependency.setGroupId(dependencyTarget.getGroupId());
                        dependency.setArtifactId(dependencyTarget.getArtifactId());
                        dependency.setVersion(dependencyTarget.getVersion());
                        String scope = dependencyTarget.getScope();
                        if (scope != null)
                        {
                            dependency.setScope(scope);
                        }

                        sticky = true; // Must write back
                    }
                }

                // If altered then save back
                if (sticky)
                {
                    logger.info("Writing " + file.getAbsolutePath());
                    writePom(model, file);
                }

            }
            catch (Exception ex)
            {
                logger.error("Couldn't read pom, " + pomPath);
            }
        }

    }

    /**
     * Write pom to file.
     * 
     * @param model
     * @param projectFiles 
     */
    public void writePom(Model model, File file)
    {
        String path = file.getAbsolutePath();

        MavenXpp3Writer mavenXpp3Writer = new MavenXpp3Writer();

        Writer fileWriter = null;
        try
        {
            fileWriter = WriterFactory.newXmlWriter(file);

            mavenXpp3Writer.write(fileWriter, model);
        }
        catch (IOException e)
        {
            logger.error(e.getMessage());
        }
        finally
        {
            IOUtil.close(fileWriter);
        }
    }

    /**
     * Get pom files.
     * 
     * @param sourcePath 
     *                  The source directory path.
     * 
     * @return List of pom files.
     * 
     * @throws IOException 
     */
    private List<File> buildPomFilesList(String sourcePath) throws IOException
    {
        List<File> results = new LinkedList<File>();

        IOFileFilter dirFilter = DirectoryFileFilter.DIRECTORY;

        // Create a filter for Files ending in ".xml"
        String suffix = ".xml";
        IOFileFilter fileFilter = FileFilterUtils.suffixFileFilter(suffix);

        java.io.FileFilter targetFilter = FileFilterUtils.or(dirFilter, fileFilter);

        // Find all target files.
        ProjectFileFinder projectFileFinder = new ProjectFileFinder(targetFilter, -1);

        results = projectFileFinder.find(sourcePath);

        return results;
    }

    /**
     * Load mappings.
     *
     * @param name
     *            The resource file name.
     * @throws Exception
     */
    public List<Mapping> loadDependencyMappings(String path) throws PommelException
    {
        logger.debug("Load mapping definitions..");

        List<Mapping> results = null;

        try
        {
            InputStream inputStream = null;

            // Try to load as a file
            inputStream = new FileInputStream(path);

            if (inputStream != null)
            {
                JAXBContext jaxbContext = JAXBContext.newInstance(Mappings.class);
                Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
                Mappings mappings = ((Mappings) unmarshaller.unmarshal(inputStream));

                results = mappings.getMapping();
            }
            else
            {
                throw new PommelException("Can't find mapping file.");
            }
        }
        catch (JAXBException e)
        {
            throw new PommelException(e.getLocalizedMessage());
        }
        catch (FileNotFoundException e)
        {
            throw new PommelException(e.getLocalizedMessage());
        }

        return results;

    }

    /**
     * Read in pom pointed to by path.
     * 
     * @param pomPath
     * 
     * @return The loaded pom model.
     * 
     * @throws Exception 
     */
    private Model readPom(String path) throws PommelException
    {
        Model model = null;

        InputStream inputStream;
        try
        {
            inputStream = new FileInputStream(path);
            model = new MavenXpp3Reader().read(inputStream);
        }
        catch (FileNotFoundException ex)
        {
            throw new PommelException(ex.getMessage());
        }
        catch (IOException ex)
        {
            throw new PommelException(ex.getMessage());
        }
        catch (XmlPullParserException ex)
        {
            throw new PommelException(ex.getMessage());
        }

        return model;
    }

    /*
     * Project File Finder class.
     * 
     * This class takes a target directory name and builds a list of files which 
     * reside under that name. There can be multiple instances i.e multiple projects.
     * 
     */
    private class ProjectFileFinder extends DirectoryWalker
    {
        private List results;

        /**
         * Takes target directory name and filter object.
         * 
         * @param target If this is null then 
         * @param fileFilter 
         */
        public ProjectFileFinder(FileFilter fileFilter, int depth)
        {
            super(fileFilter, depth);
        }

        /**
         * Find instances of named target directory starting at the path specified.
         * 
         * @param path The starting directory.
         * @param target The name of the target directory to search for.
         * 
         * @return List of search results.
         * 
         * @throws IOException 
         */
        public List find(String sourcePath) throws IOException
        {
            this.results = new ArrayList();

            File file = new File(sourcePath);

            walk(file, results);

            return results;
        }

        /**
         * Handle hitting a file.
         * 
         * @param file
         * @param depth
         * @param results
         * @throws IOException 
         */
        @Override
        protected void handleFile(File file, int depth, Collection results) throws IOException
        {
            // Only add pom files.
            String name = file.getName();
            if (name.equals(FILENAME_POM))
            {
                results.add(file);
            }
        }
    }

    /**
     * Implements search predicate.
     * 
     */
    private class DependencyPredicate implements Predicate
    {
        String groupId;
        String artifactId;
        String version;

        public DependencyPredicate(String groupId, String artifactId, String version)
        {
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.version = version;
        }

        /**
         * Check to see the the collection item matches the target.
         * 
         * @param object
         * 
         * @return True if match.
         */
        @Override
        public boolean evaluate(Object object)
        {
            boolean status = false;

            Mapping mapping = (Mapping) object;

            // Only mappings with a source and target are valid.
            if (mapping.getDependencySource() != null)
            {
                DependencySource dependencySource = mapping.getDependencySource();

                String sourceGroupId = dependencySource.getGroupId();
                String sourceArtifactId = dependencySource.getArtifactId();
                String sourceVersion = dependencySource.getVersion();

                if (sourceVersion.equals(TEXT_WILDCARD))
                {
                    status = sourceGroupId.equals(groupId) && sourceArtifactId.equals(artifactId);
                }
                else
                {
                    status = sourceGroupId.equals(groupId) && sourceArtifactId.equals(artifactId) && sourceVersion.equals(version);
                }
            }

            return status;
        }
    }
}
