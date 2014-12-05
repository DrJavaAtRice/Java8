package edu.rice.cs.dynamicjava.kernel;

import edu.rice.cs.dynamicjava.interpreter.Interpreter;
import edu.rice.cs.dynamicjava.symbol.LocalVariable;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class Autocompleter {
  private Map<String, List<Class<?>>> packageClasses;
  private Set<String> importedPackages;
  private Interpreter i;

  private static final String[] reserved = {
      "abstract", "assert", "boolean", "break", "byte", "case", "catch",
      "char", "class", "const", "continue", "default", "do", "double",
      "else", "enum", "extends", "final", "finally", "float", "for",
      "goto", "if", "implements", "import", "instanceof", "int", "interface",
      "long", "native", "new", "package", "private", "protected", "public",
      "return", "short", "static", "strictfp", "super", "switch", "synchronized",
      "this", "throw", "throws", "transient", "try", "void", "volatile", "while"
  };

  private URL jdkURL;

  public Autocompleter(Interpreter i) {
    this.i = i;

    try {
      jdkURL = new URL("file:///System/Library//Java/JavaVirtualMachines/1.6.0.jdk/Contents/Classes/");
    } catch (MalformedURLException e) {
      e.printStackTrace();
    }

    packageClasses = getPackageClasses(jdkURL);
    importedPackages = new HashSet<String>();
    importedPackages.add("java.lang");
  }

  private String[] splitDottedName(String name) {
    return name.split(".");
  }

  /**
   * Given a string of program text, return the last dotted name in the string. For example, when run on the string "x =
   * System.out", should return "System.out".
   *
   * @param query A string of program text.
   * @return The last dotted name in the string. If there is no dotted name, returns "".
   */
  private String getLastDottedName(String query) {
    String name = query;
    for (int i = query.length() - 1; i >= 0; i--) {
      char c = query.charAt(i);
      if (!(Character.isLetterOrDigit(c) || c == '.')) {
        name = query.substring(i + 1);
        break;
      }
    }

    return name;
  }

  /**
   * Create a mapping from package names to the classes in the package. Given a directory to search, find all the jars
   * in that directory and inspect their packages and classes.
   *
   * @return A mapping from package names to class lists.
   */
  private Map<String, List<Class<?>>> getPackageClasses(URL searchURL) {
    Map<String, List<Class<?>>> packageClasses = new HashMap<String, List<Class<?>>>();
    Queue<File> directories = new LinkedList<File>();
    String path = searchURL.getPath();
    URLClassLoader loader = new URLClassLoader(new URL[]{searchURL});

    if (path.endsWith(".jar")) {
      getPackageClassesFromJar(loader, path, packageClasses);
    } else {
      File f = new File(path);
      if (f.isDirectory()) {
        directories.add(f);
      }
    }

    while (directories.size() > 0) {
      File dir = directories.remove();
      for (File f : dir.listFiles()) {
        if (f.isDirectory()) {
          directories.add(f);
        } else {
          if (f.getPath().endsWith(".jar")) {
            getPackageClassesFromJar(loader, f.getPath(), packageClasses);
          }
        }
      }
    }

    return packageClasses;
  }

  /**
   * Load all the classes in a jar into a mapping from package name to classes. Modifies packageClasses in place.
   *
   * @param loader         The classloader to use when loading classes.
   * @param path           The path to the jar.
   * @param packageClasses The mapping from package names to classes.
   */
  private void getPackageClassesFromJar(ClassLoader loader, String path, Map<String, List<Class<?>>> packageClasses) {
    JarFile jarFile = null;
    try {
      jarFile = new JarFile(path);
      Enumeration<JarEntry> entries = jarFile.entries();
      while (entries.hasMoreElements()) {
        JarEntry entry = entries.nextElement();

        String entryName = entry.getName();
        if (entryName.endsWith(".class")) {
          String packageName = entryName.substring(0, entryName.lastIndexOf("/")).replace('/', '.');
          if (!packageClasses.containsKey(packageName)) {
            packageClasses.put(packageName, new ArrayList<Class<?>>(0));
          }

          String className = entryName.substring(0, entryName.length() - 6).replace('/', '.');

          try {
            packageClasses.get(packageName).add(loader.loadClass(className));
          } catch (ClassNotFoundException e) {
            //System.out.format("Failed to load class %s\n", className);
          }
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      try {
        if (jarFile != null) {
          jarFile.close();
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

  public String resolvePackage(String name) {
    String[] nameParts = splitDottedName(name);
    String packageName = "";
    for (String part : nameParts) {
      packageName += "." + part;
      if (packageClasses.keySet().contains(packageName)) {
        return packageName;
      }
    }
    return "";
  }

  private boolean matches(String prefix, String name) {
    return name.startsWith(prefix) && !name.equals(prefix);
  }

  private Set<String> resolveDottedName(Class<?> base, List<String> name) {
    Set<String> results = new HashSet<String>(100);

    // Resolve the field after the dot. For example, if the input is "x.field1.field2.methodPrefix", we should resolve
    // to field2. The name after the final dot is assumed to be partial, so it will be looked up in the field.
    String part;
    boolean matchedPart = false;
    while (name.size() > 1) {
      part = name.remove(0);
      for (Field f : base.getFields()) {
        if (f.getName() == part) {
          base = f.getClass();
          matchedPart = true;
        }
      }
      if (!matchedPart) { return results; }
    }

    // Take the final part of the name and look it up in the preceding field.
    part = name.remove(0);

    Field[] fields = base.getFields();
    for (Field f : fields) {
      String fieldName = f.getName();
      if (matches(part, fieldName)) {
        results.add(fieldName);
      }
    }

    Method[] methods = base.getMethods();
    for (Method m : methods) {
      String methodName = m.getName();
      if (matches(part, methodName)) {
        results.add(methodName);
      }
    }

    return results;
  }

  private Set<String> completeLocalVariableContents(String name) {
    Set<String> results = new HashSet<String>(100);
    Map<LocalVariable, Object> locals = i.getLocalVariables();

    if (name.indexOf(".") == -1) {
      return results;
    }

    // Split the name into components.
    List<String> nameParts = new ArrayList<String>(Arrays.asList(name.split("\\.")));
    if (name.endsWith(".")) {
      nameParts.add("");
    }

    // Attempt to look up the first component as a local variable.
    String part = nameParts.remove(0);
    Class<?> partClass = null;
    for (LocalVariable l : locals.keySet()) {
      String varName = l.declaredName();
      if (part.equals(varName)) {
        partClass = locals.get(l).getClass();
        break;
      }
    }

    // If the looking up the first component of the name fails, then it must not be a local variable.
    if (partClass == null) {
      return results;
    }

    return resolveDottedName(partClass, nameParts);
  }

  private List<String> completeLocalVariable(String name) {
    List<String> results = new LinkedList<String>();
    for (LocalVariable l : i.getLocalVariables().keySet()) {
      String varName = l.declaredName();
      if (matches(name, varName)) {
        results.add(varName);
      }
    }
    return results;
  }

  private List<String> completeReserved(String name) {
    List<String> results = new LinkedList<String>();
    for (String r : reserved) {
      if (matches(name, r)) {
        results.add(name);
      }
    }
    return results;
  }

  private List<String> completePackageName(String name) {
    List<String> results = new LinkedList<String>();
    for (String packageName : packageClasses.keySet()) {
      if (matches(name, packageName)) {
        results.add(packageName);
      }
    }
    return results;
  }

  private List<String> completeQualifiedClass(String name) {
    List<String> results = new LinkedList<String>();

    if (!name.contains(".")) {
      return results;
    }
    String packageName = name.substring(0, name.indexOf("."));
    String classPrefix = name.substring(name.indexOf(".") + 1);

    if (packageClasses.containsKey(packageName)) {
      for (Class<?> c : packageClasses.get(packageName)) {
        String className = c.getSimpleName();
        if (matches(classPrefix, className)) {
          results.add(className);
        }
      }
    }
    return results;
  }

  private List<String> completeImportedClass(String name) {
    List<String> results = new LinkedList<String>();

    for (String packageName : importedPackages) {
      if (packageClasses.containsKey(packageName)) {
        for (Class<?> c : packageClasses.get(packageName)) {
          String className = c.getSimpleName();
          if (matches(name, className)) {
            results.add(className);
          }
        }
      }
    }

    return results;
  }

  private Set<String> completeImportedClassContents(String name) {
    Set<String> results = new HashSet<String>(100);
    Map<LocalVariable, Object> locals = i.getLocalVariables();

    if (name.indexOf(".") == -1) {
      return results;
    }

    // Split the name into components.
    List<String> nameParts = new ArrayList<String>(Arrays.asList(name.split("\\.")));
    if (name.endsWith(".")) {
      nameParts.add("");
    }

    // Attempt to look up the first component as a class
    String part = nameParts.remove(0);
    Class<?> partClass = null;
    for (String packageName : importedPackages) {
      if (packageClasses.containsKey(packageName)) {
        for (Class<?> c : packageClasses.get(packageName)) {
          String className = c.getSimpleName();
          if (matches(part, className)) {
            partClass = c;
            break;
          }
        }
      }
    }

    // If the looking up the first component of the name fails, then it must not be a local variable.
    if (partClass == null) {
      return results;
    }

    return resolveDottedName(partClass, nameParts);
  }

  public String[] complete(String query) {
    List<String> results = new ArrayList<String>(100);

    String name = getLastDottedName(query);

    results.addAll(completeLocalVariable(name));
    results.addAll(completeLocalVariableContents(name));
    results.addAll(completeReserved(name));
    results.addAll(completePackageName(name));
    results.addAll(completeQualifiedClass(name));
    results.addAll(completeImportedClass(name));
    results.addAll(completeImportedClassContents(name));

    return results.toArray(new String[results.size()]);
  }
}
