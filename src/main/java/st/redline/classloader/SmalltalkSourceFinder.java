package st.redline.classloader;

import java.io.*;
import java.util.*;
import java.util.jar.*;

import static st.redline.classloader.SmalltalkSourceFile.*;

public class SmalltalkSourceFinder implements SourceFinder {

    private final SourceFactory sourceFactory;
    private final String[] classPaths;

    public SmalltalkSourceFinder(SourceFactory sourceFactory, String[] classPaths) {
        this.sourceFactory = sourceFactory;
        this.classPaths = classPaths;
    }

    public Source find(String name) {
        String filename = toFilename(name);
        File file = new File(filename);
        if (!file.exists())
            return new SourceNotFound();
        return sourceFile(filename, file);
    }

    public List<Source> findIn(String packageName) {
        System.out.println("findIn: " + packageName);
        return findInPath(packageName);
    }

    private List<Source> findInPath(String path) {
        String packagePath = path.replace(".", SEPARATOR);
        List<Source> sources = new ArrayList<>();
        for (String classPath : classPaths)
            sources.addAll(findInPath(packagePath, classPath));
        return sources;
    }

    public List<Source> findInPath(String packagePath, String classPath) {
        if (isJar(classPath)) {
            return findSourceInInJar(packagePath, classPath);
        } else
            return findSourceInFile(packagePath, classPath);
    }

    @SuppressWarnings("unchecked")
    private List<Source> findSourceInFile(String packagePath, String classPath) {
        File folder = new File(classPath + SEPARATOR + packagePath);
        if (!folder.isDirectory())
            return Collections.EMPTY_LIST;
        List<Source> sources = new ArrayList<>();
        File[] files = folder.listFiles();
        if (files != null)
            for (File file : files)
                if (file.isFile() && file.getName().endsWith(SOURCE_EXTENSION))
                    sources.add(sourceFactory.createFromFile(packagePath + SEPARATOR + file.getName(), file));
        return sources;
    }

    private List<Source> findSourceInInJar(String packagePath, String classPath) {
        List<Source> sources = new ArrayList<Source>();
        JarFile jarFile = tryCreateJarFile(classPath);
        for (Enumeration em1 = jarFile.entries(); em1.hasMoreElements();) {
            String entry = em1.nextElement().toString();
            int lastSlash = entry.lastIndexOf('/');
            int pathLength = packagePath.length();
            if (entry.startsWith(packagePath) && pathLength == lastSlash && entry.endsWith(".st"))
                sources.add(sourceFactory.createFromJar(entry, classPath));
        }
        return sources;
    }

    private JarFile tryCreateJarFile(String classPath) {
        try {
            return createJarFile(classPath);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public JarFile createJarFile(String classPath) throws IOException {
        return new JarFile(classPath);
    }

    private boolean isJar(String classPath) {
        return classPath.endsWith(".jar") || classPath.endsWith(".JAR");
    }

    private Source sourceFile(String filename, File file) {
        return sourceFactory.createFromFile(filename, file);
    }

    private String toFilename(String name) {
        return name.replaceAll("\\.", File.separator) + SOURCE_EXTENSION;
    }

    public class SourceNotFound implements Source {

        public boolean hasContent() {
            return false;
        }

        public String contents() {
            return "";
        }

        public String className() {
            return "";
        }

        public String fullClassName() {
            return "";
        }

        public String fileExtension() {
            return "";
        }

        public String packageName() {
            return "";
        }
    }
}
