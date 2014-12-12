# DynamicJava

DynamicJava is a REPL for Java code.

## IPython Kernel

DynamicJava includes a built-in [IPython](http://ipython.org) kernel. The kernel enables execution of Java code in the IPython terminal application as well as in its web based [notebook interface](http://ipython.org/notebook.html).

### Prerequisites

1. Install IPython. It can be installed using `pip` with `pip install ipython` or through your package manager.

2. Create a new IPython profile for DynamicJava. Run `ipython profile create dynamicjava`. This will create a new IPython profile in `.ipython/profile_dynamicjava`.

3. Configure the new profile to run the DynamicJava kernel. Copy the following lines into `.ipython/profile_dynamicjava/ipython_config.py`, replacing `DYNAMICJAVA_PATH` with the file path of `dynamicjava.jar`. Any additional arguments to the JVM should be passed here.

   ```
   c.KernelManager.kernel_cmd = [
       "java",
       "-jar", "DYNAMICJAVA_PATH",
       "-kernel", "{connection_file}"
   ]
   c.Session.key = b''
   c.Session.keyfile = b''
   ```
   
4. (Optional) To debug the kernel while it is running, add the following line to the `kernel_cmd` list.

   ```
   "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=1044",
   ```
   
   To use the debugger, follow the instructions to configure remote debugging for your IDE ([Eclipse](http://help.eclipse.org/luna/index.jsp?topic=%2Forg.eclipse.jdt.doc.user%2Ftasks%2Ftask-remotejava_launch_config.htm), [IntelliJ](https://www.jetbrains.com/idea/help/run-debug-configuration-remote.html))
   
5. Configure the IPython Notebook Java syntax highlighting. Copy the files in `profile/static/custom/` to `.ipython/profile_dynamicjava/static/custom/`.

### Building and Running

* To build the kernel, run `ant jar`.
* The IPython console frontend can be run with `ipython console --profile dynamicjava`.
* To run the web interface, run `ipython notebook --profile dynamicjava`.
