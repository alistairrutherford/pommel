# pommel
pommel
This tool will scan any number of maven projects and replace target dependencies. The inputs are a top level directory and a path to the mapping file. The mapping file contains the source and target dependency list.

This is a companion tool to mavenize which will take existing java projects and reconfigure them into the maven directory structure. See links on this page.

Example
Say, for instance, you want to update all the junit dependencies in a number of java projects. Also you want to make sure the dependency scope is set to "test". You create a mapping file like the following:

```xml
<?xml version="1.0"?>
<mappings>
  <mapping>
    <dependency-source>
        <groupId>junit</groupId>
        <artifactId>junit</artifactId>
        <version>4.0</version>
    </dependency-source>
    <dependency-target>
        <groupId>junit</groupId>
        <artifactId>junit</artifactId>
        <version>4.9</version>
        <scope>test</scope>
    </dependency-target>
  </mapping>

</mappings>
```
You can also specify a wildcard for the source version if you want to match any version of junit i.e..

```xml
<?xml version="1.0"?>
<mappings>
  <mapping>
    <dependency-source>
        <groupId>junit</groupId>
        <artifactId>junit</artifactId>
        <version>*</version>
    </dependency-source>
    <dependency-target>
        <groupId>junit</groupId>
        <artifactId>junit</artifactId>
        <version>4.9</version>
        <scope>test</scope>
    </dependency-target>
  </mapping>

</mappings>
```
Now simply point the tool at your projects with the directory and mapping file as the parameters i.e.

java -jar pommel-1.0.0.jar -iC:\Development\java\projects -mmapping.xml
The tool will replace each reference to junit with junit 4.9 and fix the scope in every pom file it finds under the input directory.

Arguments
-i {input directory path}
-m {path to mapping file}
How it Works
The tools gathers links to all the pom files under the specified directory. It will then walk through each dependency in each pom file looking for matches from the mapping file. Each time it make a match it will replace it with the one from the mapping file. The pom file will then be written back.

Tips
THIS APPLICATION WILL REWRITE YOUR POM FILE. MAKE SURE YOU HAVE BACKUPS BEFORE APPLYING IT.

Java
Uses Java 1.6+
