Codealike-Eclipse
=================

Pre-requisites:

1. Java 11
2. Eclipse PDE - https://www.eclipse.org/pde/

## Building

Open Eclipse PDE

Select Import -> General -> Existing Projects into Workspace

Select the root directory where the repository was cloned

Check "Search for nested projects". Select only the bottom the projects. Don't select the project "Codealike-Eclipse"

Click "Finish"

Click Project -> Clean to rebuild everything

## Running

Select the project "com.codealike.client.eclipse" and right-click on it.

Click "Run As" -> "Run Configuration"

Right click "Eclipse Application" and click "New Configuration"

Enter any name and click "Apply"

Click "Run" to test the plugin

## Plugin usage

In the first Eclipse is executed, the Codealike token will be asked. The token can be retrieved in https://codealike.com

You can always change the token in "Codealike" menu

Once the token is configured properly, it will start tracking the work and send to your account dashboard in https://codealike.com
