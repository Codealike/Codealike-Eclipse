Codealike-Eclipse
=================

Pre-requisites:

1. Java 11+
2. Maven 3.6+
3. Eclipse PDE - https://www.eclipse.org/pde/
4. Eclipse Maven plugin

## Preparation

Clone the repository

In the command line run the following commands:

```
$ mvn clean
$ mvn initialize compile
```

## Building

Open Eclipse PDE

Select Import -> General -> Existing Projects into Workspace

Select the root directory where the repository was cloned

Check "Search for nested projects". Select all projects.

Click "Finish"

Click Project -> Clean to rebuild everything

## Running

Select the project "com.codealike.client.eclipse" and right-click on it.

Click "Run As" -> "Run Configuration"

Right click "Eclipse Application" and click "New Configuration"

Enter any name and click "Apply"

Click "Run" to test the plugin

## Generating plugin package

In the command line run the following command:

```
$ mvn package
```

Plugin zip file will be located in the `com.codealike.client.eclipse.site/target` folder.

## Plugin usage

In the first Eclipse is executed, the Codealike token will be asked. The token can be retrieved in https://codealike.com

You can always change the token in "Codealike" menu

Once the token is configured properly, it will start tracking the work and send to your account dashboard in https://codealike.com

## Background

This project is built using Eclipse Tycho (https://www.eclipse.org/tycho/) and requires at least maven 3.6 (http://maven.apache.org/download.html) to be built via CLI. 
Simply run :

    mvn install

The first run will take quite a while since maven will download all the required dependencies in order to build everything.

In order to use the generated eclipse plugins in Eclipse, you will need m2e (https://www.eclipse.org/m2e) 
and the m2eclipse-tycho plugin (https://github.com/tesla/m2eclipse-tycho/). Update sites to install these plugins : 

* m2e stable update site : http://download.eclipse.org/technology/m2e/releases/
* m2eclipse-tycho dev update site : http://repo1.maven.org/maven2/.m2e/connectors/m2eclipse-tycho/0.7.0/N/0.7.0.201309291400/

