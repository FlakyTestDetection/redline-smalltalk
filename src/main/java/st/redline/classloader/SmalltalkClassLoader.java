/* Redline Smalltalk, Copyright (c) James C. Ladd. All rights reserved. See LICENSE in the root of this distribution. */
package st.redline.classloader;

import st.redline.compiler.Compiler;
import st.redline.core.PrimObject;

import java.util.*;

import static st.redline.compiler.SmalltalkGeneratingVisitor.DEFAULT_IMPORTED_PACKAGE;

public class SmalltalkClassLoader extends ClassLoader {

    // Special Object instance values set during bootstrapping.
    private static PrimObject NIL;
    private static PrimObject TRUE;
    private static PrimObject FALSE;

    private final SourceFinder sourceFinder;
    private final Map<String, Class> classCache;
    private final Map<String, PrimObject> objectCache;
    private final Map<String, Map<String, String>> packageCache;
    private final Stack<String> instantiatingName;
    private boolean bootstrapping;

    public SmalltalkClassLoader(ClassLoader classLoader, SourceFinder sourceFinder, Bootstrapper bootstrapper) {
        super(classLoader);
        this.sourceFinder = sourceFinder;
        this.classCache = new HashMap<>();
        this.objectCache = new HashMap<>();
        this.packageCache = new HashMap<>();
        this.instantiatingName = new Stack<String>();

        // initialize Object cache with bootstrapped objects.
        bootstrapper.bootstrap(this);
    }

    public PrimObject findObject(String name) {
        System.out.println("** findObject " + name);
        PrimObject cls = cachedObject(name);
        if (cls != null)
            return cls;
        try {
            boolean requiresInstantiation = !isCachedClass(name);
            Class messageSendingClass = findClass(name);
            if (requiresInstantiation)
                instantiateMessageSendingClass(messageSendingClass, name);
            cls = cachedObject(name);
            if (cls != null)
                return cls;
        } catch (Exception e) {
            e.printStackTrace();
        }
        throw new ObjectNotFoundException("Object '" + name + "' was not found.");
    }

    private void instantiateMessageSendingClass(Class messageSendingClass, String name) throws IllegalAccessException, InstantiationException {
        // We have to instantiate the class to cause all the message sends contained within it to be sent.
        // Should these message sends involve a subclass then we need to ensure that subclass knows the package it is in.
        // hence this code (which makes me uncomfortable).
        pushInstantiatingName(name);
        try {
            messageSendingClass.newInstance();
        } finally {
            popInstantiatingName();
        }
    }

    public String peekInstantiationName() {
        return instantiatingName.peek();
    }

    private void popInstantiatingName() {
        instantiatingName.pop();
    }

    private void pushInstantiatingName(String name) {
        instantiatingName.push(name);
    }

    protected PrimObject cachedObject(String name) {
        System.out.println("** cachedObject " + name);
        return objectCache.get(name);
    }

    public void cacheObject(String name, PrimObject object) {
        System.out.println("** cacheObject " + object + " as " + name);
        objectCache.put(name, object);
    }

    public Class findClass(String name) throws ClassNotFoundException {
        System.out.println("** findClass " + name);
        Class cls = cachedClass(name);
        if (cls != null)
            return cls;
        byte[] classData = loadClassData(name);
        if (classData == null)
            return super.findClass(name);
        cls = defineClass(null, classData, 0, classData.length);
        cacheClass(cls, name);
        return cls;
    }

    private void cacheClass(Class cls, String name) {
        System.out.println("** cacheClass " + cls + " as " + name);
        classCache.put(name, cls);
    }

    private boolean isCachedClass(String name) {
        return classCache.containsKey(name);
    }

    private Class cachedClass(String name) {
        return classCache.get(name);
    }

    private byte[] loadClassData(String name) {
        return compile(findSource(name));
    }

    private Source findSource(String name) {
        return sourceFinder.find(name);
    }

    private byte[] compile(Source source) {
        return compiler(source).compile();
    }

    private Compiler compiler(Source source) {
        return new Compiler(source);
    }

    public void beginBootstrapping() {
        bootstrapping = true;
    }

    public void endBootstrapping() {
        bootstrapping = false;
    }

    public boolean isBootstrapping() {
        return bootstrapping;
    }

    public void nilInstance(PrimObject nil) {
        NIL = nil;
    }

    public PrimObject nilInstance() {
        return NIL;
    }

    public void falseInstance(PrimObject instance) {
        FALSE = instance;
    }

    public PrimObject falseInstance() {
        return FALSE;
    }

    public void trueInstance(PrimObject instance) {
        TRUE = instance;
    }

    public PrimObject trueInstance() {
        return TRUE;
    }

    @SuppressWarnings("unchecked")
    public String importForBy(String name, String packageName) {
        System.out.println("** importFor: " + name + " in " + packageName);
        Map<String, String> imports = packageCache.getOrDefault(packageName, Collections.EMPTY_MAP);
        String fqn = imports.get(name);
        if (fqn != null)
            return fqn;
        if (!DEFAULT_IMPORTED_PACKAGE.equals(packageName))
            return importForBy(name, DEFAULT_IMPORTED_PACKAGE);
        return name;
    }

    public void importAll(String packageName) {
        System.out.println("** importAll: " + packageName);
        if (!packageCache.containsKey(packageName))
            for (Source source : sourceFinder.findIn(packageName))
                addImport(packageName, source);
    }

    private void addImport(String packageName, Source source) {
        System.out.println("** addImport: " + packageName + " " + source.className());
        Map<String, String> objects = packageCache.getOrDefault(packageName, new HashMap<>());
        objects.put(source.className(), packageName + "." + source.className());
        packageCache.put(packageName, objects);
    }
}
