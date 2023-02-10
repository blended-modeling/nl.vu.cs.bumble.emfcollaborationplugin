# Collaboration Plugin (Eclipse side)

## Installation Guide

### Prerequisites

Following tools need to be installed first.

|framework|version|
|:-:|:-:|
|Eclipse Modeling Tools|>=2022|
|Java|11 & 17|

### Dependent Projects

In order to run the collaboration plugin, the following projects also need to be installed. Please download them along with this project and follow the instructions stated in  **Install** section.

|Project|link|
|:-:|:-:|
|EMF.cloud server (modified)|[repository](https://github.com/Yunabell-VU/emfcloud-modelserver-collaboration-plugin)|
|demo metamodel|[repository](https://github.com/blended-modeling/bumblestatemachine)|
|demo model|[repository](https://github.com/Yunabell-VU/nl.vu.cs.bumble.trafficsignals.statemachine)|

### Install

Open Eclipse Modeling Tools with an empty workspace, import (`File` -> `Open projects from file system`) the following projects one by one :
1. EMF.cloud server
2. demo metamodel (statemachine)
3. collaboration plugin (this project)

**EMF server**
Make sure the server can run properly. You may need to install `Eclipse m2e` plugin from the marketplace.

In the `settings` -> `Plug-in Development` -> `Target Flatform` choose any of the `EMF.cloud Model Server Targetplatform` and apply it.

**statemachine metamodel**
After the metamodel is imported, you need to generate the editors. 
(Go to the `model` folder in the metamodel -> double click `statemachine.genmodel` -> right click the `Statemachine` model showed in the editor, choose `Generate All`)

**collaboration plugin**
Run the plugin with Java 17.   

(`src` -> right click `Activator` -> Run As -> Run Configurations -> Eclipse Application -> Execution Environment (Java Runtime Environment) -> Java 17) 

**At run time**
In the opened Editor of collaboration plugin, import the demo model (TrafficSignals) through file system.

----