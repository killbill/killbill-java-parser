package com.ning.killbill.generators.ruby;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.TreeSet;

import com.ning.killbill.generators.GeneratorException;
import com.ning.killbill.objects.ClassEnumOrInterface;
import com.ning.killbill.objects.Field;
import com.ning.killbill.objects.Method;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;

public class JRubyPluginGenerator extends BaseGenerator {

    private final static String LICENSE_NAME = "RubyLicense.txt";
    private final static int INDENT_LEVEL = 2;
    private static final String REQUIRE_PREFIX = "killbill/gen";

    private final String[] POJO_MODULES = {"Killbill", "Plugin", "Model"};
    private final String[] API_MODULES = {"Killbill", "Plugin", "Api"};

    public JRubyPluginGenerator() {
        super();
    }

    @Override
    protected void startGeneration(final List<ClassEnumOrInterface> classes, final File outputDir) throws GeneratorException {

    }


    @Override
    protected void generateClass(final ClassEnumOrInterface obj, final List<ClassEnumOrInterface> allClasses, final File outputDir) throws GeneratorException {

        resetIndentation();

        final File output = new File(outputDir, createFileName(obj.getName(), true));
        writeLicense(output);


        Writer w = null;
        try {
            w = new FileWriter(output, true);

            writeHeader(w);

            final boolean isInterface = obj.isInterface();
            final boolean isApi = isInterface && isApiFile(obj.getName());

            generateStartModules(w, isApi);

            final List<Method> flattenedMethods = new ArrayList<Method>();
            flattenedMethods.addAll(getTopMethods(obj, allClasses));
            flattenedMethods.addAll(obj.getMethods());

            dedupPreserveOrder(flattenedMethods);

            writeNewLine(w);

            int curIndent = INDENT_LEVEL;
            if (isInterface) {
                writeWithIndentationAndNewLine("java_package '" + obj.getPackageName() + "'", w, curIndent);
                curIndent = 0;
            }
            writeWithIndentationAndNewLine("class " + obj.getName(), w, curIndent);
            writeNewLine(w);

            curIndent = INDENT_LEVEL;
            if (isInterface) {
                writeWithIndentationAndNewLine("include " + obj.getPackageName() + "." + obj.getName(), w, curIndent);
                curIndent = 0;
            }
            writeNewLine(w);

            if (isApi) {
                generateForApi(obj, w, flattenedMethods);
            } else {
                generateForPojo(obj, w, flattenedMethods);
            }


            writeWithIndentationAndNewLine("end", w, -INDENT_LEVEL);
            generateEndModules(w, isApi);
            w.flush();
            w.close();

        } catch (FileNotFoundException e) {
            throw new GeneratorException("Failed to generate file " + obj.getName(), e);
        } catch (IOException e) {
            throw new GeneratorException("Failed to generate file " + obj.getName(), e);
        }
    }

    private void generateEndModules(final Writer w, boolean api) throws IOException {

        final String[] modules = api ? API_MODULES : POJO_MODULES;
        for (int i = 0; i < modules.length; i++) {
            writeWithIndentationAndNewLine("end", w, -INDENT_LEVEL);
        }
    }

    private void generateStartModules(final Writer w, boolean api) throws IOException {
        final String[] modules = api ? API_MODULES : POJO_MODULES;
        boolean first = true;
        for (int i = 0; i < modules.length; i++) {
            if (first) {
                writeWithIndentationAndNewLine("module " + modules[i], w, 0);
                first = false;
            } else {
                writeWithIndentationAndNewLine("module " + modules[i], w, INDENT_LEVEL);
            }
        }
    }


    private void generateForApi(final ClassEnumOrInterface obj, final Writer w, final List<Method> flattenedMethods) throws IOException, GeneratorException {
        writeWithIndentationAndNewLine("def initialize(real_java_api)", w, 0);
        writeWithIndentationAndNewLine("@real_java_api = real_java_api", w, INDENT_LEVEL);
        writeWithIndentationAndNewLine("end", w, -INDENT_LEVEL);
        writeNewLine(w);

        for (final Method m : flattenedMethods) {
            final String returnValue = m.getReturnValueType().getBaseType();
            writeNewLine(w);
            writeWithIndentation("java_signature 'Java::" + returnValue + " " + m.getName() + "(", w, 0);
            boolean first = true;
            for (Field f : m.getOrderedArguments()) {
                if (!first) {
                    writeAppend(", ", w);
                }
                writeAppend("Java::" + f.getType().getBaseType(), w);
                first = false;
            }
            writeAppend(")'", w);
            writeNewLine(w);

            final String method_name = camelToUnderscore(m.getName());
            writeWithIndentation("def " + method_name + "(", w, 0);
            first = true;
            for (Field f : m.getOrderedArguments()) {
                if (!first) {
                    writeAppend(", ", w);
                }
                writeAppend(f.getName(), w);
                first = false;
            }
            writeAppend(")", w);
            writeNewLine(w);

            boolean firstArg = true;
            for (Field f : m.getOrderedArguments()) {
                if (firstArg) {

                    writeConversionToJava(f.getName(), f.getType().getBaseType(), f.getType().getGenericType(), allClasses, w, INDENT_LEVEL, "");
                    firstArg = false;
                } else {
                    writeConversionToJava(f.getName(), f.getType().getBaseType(), f.getType().getGenericType(), allClasses, w, 0, "");
                }
                //writeWithIndentationAndNewLine(f.getName() + " = " + f.getName() + ".to_java", w, INDENT_LEVEL);
                //writeWithIndentationAndNewLine("end", w, -INDENT_LEVEL);
                //writeNewLine(w);

            }

            final boolean isVoidReturn = "void".equals(m.getReturnValueType().getBaseType());
            final boolean gotExceptions = (m.getExceptions().size() > 0);
            if (gotExceptions) {
                writeWithIndentationAndNewLine("begin", w, 0);
            }
            if (isVoidReturn) {
                writeWithIndentation("@real_java_api." + method_name + "(", w, gotExceptions ? INDENT_LEVEL : 0);
            } else {
                writeWithIndentation("res = @real_java_api." + method_name + "(", w, gotExceptions ? INDENT_LEVEL : 0);
            }


            first = true;
            for (Field f : m.getOrderedArguments()) {
                if (!first) {
                    writeAppend(", ", w);
                }
                writeAppend(f.getName(), w);
                first = false;
            }
            writeAppend(")", w);
            writeNewLine(w);
            // conversion
            if (!isVoidReturn) {
                writeConversionToRuby("res", m.getReturnValueType().getBaseType(), m.getReturnValueType().getGenericType(), allClasses, w, 0, false);
                writeWithIndentationAndNewLine("return res", w, 0);
            }

            if (gotExceptions) {
                for (String curException : m.getExceptions()) {
                    writeWithIndentationAndNewLine("rescue Java::" + curException + " => e", w, -INDENT_LEVEL);
                    try {
                        findClassEnumOrInterface(curException, allClasses);
                        final String jrubyPoJo = getJrubyPoJo(curException);
                        writeWithIndentationAndNewLine("raise " + jrubyPoJo + ".new.to_ruby(e)", w, INDENT_LEVEL);
                    } catch (GeneratorException e) {
                        writeWithIndentationAndNewLine("raise ApiException.new(\"" + curException + ": #{e.msg unless e.msg.nil?}\")", w, INDENT_LEVEL);
                    }
                }
                writeWithIndentationAndNewLine("end", w, -INDENT_LEVEL);
            }
            writeWithIndentationAndNewLine("end", w, -INDENT_LEVEL);
        }
    }

    private boolean isApiFile(final String fileName) {
        return fileName.endsWith("Api");
    }

    private void generateForPojo(final ClassEnumOrInterface obj, final Writer w, final List<Method> flattenedMethods) throws IOException, GeneratorException {
        generateAttributeAccessorFromGetterMethods(obj, flattenedMethods, w);

        writeWithIndentationAndNewLine("def initialize()", w, 0);
        writeWithIndentationAndNewLine("end", w, 0);
        writeNewLine(w);

        generateToJava(obj, flattenedMethods, w);

        generateToRuby(obj, flattenedMethods, w);
    }


    private void generateToRuby(final ClassEnumOrInterface obj, final List<Method> flattenedMethods, final Writer w) throws IOException, GeneratorException {
        boolean first = true;
        writeWithIndentationAndNewLine("def to_ruby(j_obj)", w, 0);
        for (final Method m : flattenedMethods) {
            if (!m.isGetter()) {
                continue;
            }
            if (first) {
                writeConversionToRuby(m, allClasses, w, INDENT_LEVEL);
                first = false;
            } else {
                writeNewLine(w);
                writeConversionToRuby(m, allClasses, w, 0);
            }
        }
        writeWithIndentationAndNewLine("end", w, -INDENT_LEVEL);
        writeNewLine(w);
    }

    private void writeConversionToRuby(final Method m, final List<ClassEnumOrInterface> allClasses, final Writer w, int indentOffset) throws GeneratorException, IOException {
        final String member = camelToUnderscore(convertGetterMethodToFieldName(m.getName()));
        final String returnValueType = m.getReturnValueType().getBaseType();
        final String returnValueGeneric = m.getReturnValueType().getGenericType();
        writeConversionToRuby(member, returnValueType, returnValueGeneric, allClasses, w, indentOffset, true);
    }


    private void writeConversionToRuby(final String member, final String returnValueType, final String returnValueGeneric, final List<ClassEnumOrInterface> allClasses, final Writer w, int indentOffset, boolean fromJobj) throws GeneratorException, IOException {

        writeWithIndentationAndNewLine("# conversion for " + member + " [type = " + returnValueType + "]", w, indentOffset);

        final String memberPrefix = fromJobj ? "@" : "";
        if (fromJobj) {
            writeWithIndentationAndNewLine(memberPrefix + member + " = j_obj." + member, w, 0);
        }

        if (returnValueType.equals("byte")) {
        } else if (returnValueType.equals("short") || returnValueType.equals("java.lang.Short")) {
        } else if (returnValueType.equals("int") || returnValueType.equals("java.lang.Integer")) {
        } else if (returnValueType.equals("long") || returnValueType.equals("java.lang.Long")) {
        } else if (returnValueType.equals("float") || returnValueType.equals("java.lang.Float")) {
        } else if (returnValueType.equals("double") || returnValueType.equals("java.lang.Double")) {
        } else if (returnValueType.equals("char") || returnValueType.equals("java.lang.Char")) {
        } else if (returnValueType.equals("boolean") || returnValueType.equals("java.lang.Boolean")) {
            writeWithIndentationAndNewLine("if " + memberPrefix + member + ".nil?", w, 0);
            writeWithIndentationAndNewLine(memberPrefix + member + " = false", w, INDENT_LEVEL);
            writeWithIndentationAndNewLine("else", w, -INDENT_LEVEL);
            writeWithIndentationAndNewLine("tmp_bool = (" + memberPrefix + member + ".java_kind_of? java.lang.Boolean) ? " + memberPrefix + member + ".boolean_value : " + memberPrefix + member, w, INDENT_LEVEL);
            writeWithIndentationAndNewLine(memberPrefix + member + " = tmp_bool ? true : false", w, 0);
            writeWithIndentationAndNewLine("end", w, -INDENT_LEVEL);
        } else if (returnValueType.equals("java.lang.Throwable")) {
            writeWithIndentationAndNewLine(memberPrefix + member + " = " + memberPrefix + member + ".to_s unless " + memberPrefix + member + ".nil?", w, 0);
        } else {
            if ("java.lang.String".equals(returnValueType) ||
                // TTO same thing as for to_java
                "java.lang.Object".equals(returnValueType)) {
            } else if ("java.util.UUID".equals(returnValueType)) {
                writeWithIndentationAndNewLine(memberPrefix + member + " = " + memberPrefix + member + ".nil? ? nil : " + memberPrefix + member + ".to_s", w, 0);
            } else if ("java.math.BigDecimal".equals(returnValueType)) {
                writeWithIndentationAndNewLine(memberPrefix + member + " = " + memberPrefix + member + ".nil? ? 0 : " + memberPrefix + member + ".to_s.to_i", w, 0);
            } else if ("org.joda.time.DateTime".equals(returnValueType) ||
                       "java.util.Date".equals(returnValueType)) {
                writeWithIndentationAndNewLine("if !" + memberPrefix + member + ".nil?", w, 0);
                if ("java.util.Date".equals(returnValueType)) {
                    // First convert to DateTime
                    writeWithIndentationAndNewLine(memberPrefix + member + " = " + "Java::org.joda.time.DateTime.new(" + memberPrefix + member + ")", w, INDENT_LEVEL);
                }
                writeWithIndentationAndNewLine("fmt = Java::org.joda.time.format.ISODateTimeFormat.date_time", w, ("java.util.Date".equals(returnValueType) ? 0 : INDENT_LEVEL));
                writeWithIndentationAndNewLine("str = fmt.print(" + memberPrefix + member + ")", w, 0);
                writeWithIndentationAndNewLine(memberPrefix + member + " = " + "DateTime.iso8601(str)", w, 0);
                writeWithIndentationAndNewLine("end", w, -INDENT_LEVEL);
            } else if ("org.joda.time.LocalDate".equals(returnValueType)) {
                writeWithIndentationAndNewLine("if !" + memberPrefix + member + ".nil?", w, 0);
                writeWithIndentationAndNewLine(memberPrefix + member + " = " + memberPrefix + member + ".to_s", w, INDENT_LEVEL);
                writeWithIndentationAndNewLine("end", w, -INDENT_LEVEL);
            } else if ("org.joda.time.DateTimeZone".equals(returnValueType)) {
                writeWithIndentationAndNewLine("if !" + memberPrefix + member + ".nil?", w, 0);
                writeWithIndentationAndNewLine(memberPrefix + member + " = " + "TZInfo::Timezone.get(" + memberPrefix + member + ".get_id)", w, INDENT_LEVEL);
                writeWithIndentationAndNewLine("end", w, -INDENT_LEVEL);
            } else if ("java.util.List".equals(returnValueType) ||
                       "java.util.Collection".equals(returnValueType) ||
                       "java.util.Set".equals(returnValueType) ||
                       "java.util.Iterator".equals(returnValueType)) {
                writeWithIndentationAndNewLine("tmp = []", w, 0);
                writeWithIndentationAndNewLine(memberPrefix + member + ".each do |m|", w, 0);
                writeConversionToRuby("m", returnValueGeneric, null, allClasses, w, INDENT_LEVEL, fromJobj);
                writeWithIndentationAndNewLine("tmp << m", w, 0);
                writeWithIndentationAndNewLine("end", w, -INDENT_LEVEL);
                writeWithIndentationAndNewLine(memberPrefix + member + " = tmp", w, 0);
            } else {
                // At this point if we can't find the class we throw
                final ClassEnumOrInterface classEnumOrInterface = findClassEnumOrInterface(returnValueType, allClasses);
                if (classEnumOrInterface.isEnum()) {
                    writeWithIndentationAndNewLine(memberPrefix + member + " = " + memberPrefix + member + ".to_s unless " + memberPrefix + member + ".nil?", w, 0);
                } else {
                    writeWithIndentationAndNewLine(memberPrefix + member + " = " + getJrubyPoJo(returnValueType) + ".new.to_ruby(" + memberPrefix + member + ") unless " + memberPrefix + member + ".nil?", w, 0);
                }
            }
        }
    }

    private String getJrubyPoJo(final String pojoBaseName) throws GeneratorException {
        String[] parts = pojoBaseName.split("\\.");
        final String jrubyObject = Joiner.on("::").join(POJO_MODULES) + "::" + parts[parts.length - 1];
        return jrubyObject;
    }

    private void generateToJava(final ClassEnumOrInterface obj, final List<Method> flattenedMethods, final Writer w) throws IOException, GeneratorException {
        boolean first = true;
        writeWithIndentationAndNewLine("def to_java()", w, 0);
        for (final Method m : flattenedMethods) {
            if (!m.isGetter()) {
                continue;
            }
            if (first) {
                writeConversionToJava(m, allClasses, w, INDENT_LEVEL);
                first = false;
            } else {
                writeNewLine(w);
                writeConversionToJava(m, allClasses, w, 0);
            }
        }
        writeWithIndentationAndNewLine("end", w, -INDENT_LEVEL);
        writeNewLine(w);
    }


    private void writeConversionToJava(final Method m, final List<ClassEnumOrInterface> allClasses, final Writer w, int indentOffset) throws GeneratorException, IOException {
        final String member = camelToUnderscore(convertGetterMethodToFieldName(m.getName()));
        final String returnValueType = m.getReturnValueType().getBaseType();
        final String returnValueGeneric = m.getReturnValueType().getGenericType();
        writeConversionToJava(member, returnValueType, returnValueGeneric, allClasses, w, indentOffset, "@");
    }

    private void writeConversionToJava(final String member, final String returnValueType, final String returnValueGeneric, final List<ClassEnumOrInterface> allClasses, final Writer w, int indentOffset, String memberPrefix) throws GeneratorException, IOException {

        writeWithIndentationAndNewLine("# conversion for " + member + " [type = " + returnValueType + "]", w, indentOffset);
        if (returnValueType.equals("byte")) {
            // default jruby conversion should be fine
        } else if (returnValueType.equals("short") || returnValueType.equals("java.lang.Short")) {
            // default jruby conversion should be fine
        } else if (returnValueType.equals("int") || returnValueType.equals("java.lang.Integer")) {
            // default jruby conversion should be fine
            writeWithIndentationAndNewLine(memberPrefix + member + " = " + memberPrefix + member, w, 0);
        } else if (returnValueType.equals("long") || returnValueType.equals("java.lang.Long")) {
            // default jruby conversion should be fine
            writeWithIndentationAndNewLine(memberPrefix + member + " = " + memberPrefix + member, w, 0);
        } else if (returnValueType.equals("float") || returnValueType.equals("java.lang.Float")) {
            // default jruby conversion should be fine
            writeWithIndentationAndNewLine(memberPrefix + member + " = " + memberPrefix + member, w, 0);
        } else if (returnValueType.equals("double") || returnValueType.equals("java.lang.Double")) {
            // default jruby conversion should be fine
            writeWithIndentationAndNewLine(memberPrefix + member + " = " + memberPrefix + member, w, 0);
        } else if (returnValueType.equals("char") || returnValueType.equals("java.lang.Char")) {
            // default jruby conversion should be fine
            writeWithIndentationAndNewLine(memberPrefix + member + " = " + memberPrefix + member, w, 0);
        } else if (returnValueType.equals("boolean") || returnValueType.equals("java.lang.Boolean")) {
            writeWithIndentationAndNewLine(memberPrefix + member + " = " + memberPrefix + member + ".nil? ? java.lang.Boolean.new(false) : java.lang.Boolean.new(" + memberPrefix + member + ")", w, 0);
        } else if (returnValueType.equals("java.lang.Throwable")) {
            writeWithIndentationAndNewLine(memberPrefix + member + " = " + memberPrefix + member + ".to_s unless " + member + ".nil?", w, 0);
        } else {
            if ("java.lang.String".equals(returnValueType) ||
                // TODO fix KB API really!
                // We assume Object is a string in that case
                "java.lang.Object".equals(returnValueType)) {
                // default jruby conversion should be fine
                writeWithIndentationAndNewLine(memberPrefix + member + " = " + memberPrefix + member + ".to_s unless "  + memberPrefix + member + ".nil?", w, 0);
            } else if ("java.util.UUID".equals(returnValueType)) {
                writeWithIndentationAndNewLine(memberPrefix + member + " = java.util.UUID.fromString(" + memberPrefix + member + ".to_s) unless "  + memberPrefix + member + ".nil?", w, 0);
            } else if ("java.math.BigDecimal".equals(returnValueType)) {
                writeWithIndentationAndNewLine("if " + memberPrefix + member + ".nil?", w, 0);
                writeWithIndentationAndNewLine(memberPrefix + member + " = java.math.BigDecimal::ZERO", w, INDENT_LEVEL);
                writeWithIndentationAndNewLine("else", w, -INDENT_LEVEL);
                //writeWithIndentationAndNewLine(member + " = java.math.BigDecimal.new(" + member + ".respond_to?(:cents) ? " + member + ".cents : " + member + ".to_i)", w, INDENT_LEVEL);
                writeWithIndentationAndNewLine(memberPrefix + member + " = java.math.BigDecimal.new(" + memberPrefix + member + ".to_i)", w, INDENT_LEVEL);
                writeWithIndentationAndNewLine("end", w, -INDENT_LEVEL);
            } else if ("org.joda.time.DateTime".equals(returnValueType) ||
                       "java.util.Date".equals(returnValueType)) {
                writeWithIndentationAndNewLine("if !" + memberPrefix + member + ".nil?", w, 0);
                writeWithIndentationAndNewLine(memberPrefix + member + " =  (" + memberPrefix + member + ".kind_of? Time) ? DateTime.parse(" + memberPrefix + member + ".to_s) : " + memberPrefix + member, w, INDENT_LEVEL);
                writeWithIndentationAndNewLine(memberPrefix + member + " = Java::org.joda.time.DateTime.new(" + memberPrefix + member + ".to_s, Java::org.joda.time.DateTimeZone::UTC)", w, 0);
                if ("java.util.Date".equals(returnValueType)) {
                    writeWithIndentationAndNewLine(memberPrefix + member + " = " + memberPrefix + member + ".to_date", w, 0);
                }
                writeWithIndentationAndNewLine("end", w, -INDENT_LEVEL);
            } else if ("org.joda.time.LocalDate".equals(returnValueType)) {
                writeWithIndentationAndNewLine("if !" + memberPrefix + member + ".nil?", w, 0);
                writeWithIndentationAndNewLine(memberPrefix + member + " = Java::org.joda.time.LocalDate.parse(" + memberPrefix + member + ".to_s)", w, INDENT_LEVEL);
                writeWithIndentationAndNewLine("end", w, -INDENT_LEVEL);
            } else if ("org.joda.time.DateTimeZone".equals(returnValueType)) {
                writeWithIndentationAndNewLine("if !" + memberPrefix + member + ".nil?", w, 0);
                writeWithIndentationAndNewLine(memberPrefix + member + " = Java::org.joda.time.DateTimeZone.forID(" + memberPrefix + member + ".respond_to?(:identifier) ? " + memberPrefix + member + ".identifier : " + memberPrefix + member + ".to_s)", w, INDENT_LEVEL);
                writeWithIndentationAndNewLine("end", w, -INDENT_LEVEL);
            } else if ("java.util.List".equals(returnValueType) ||
                       "java.util.Collection".equals(returnValueType) ||
                       "java.util.Set".equals(returnValueType) ||
                       "java.util.Iterator".equals(returnValueType)) {
                writeWithIndentationAndNewLine("tmp = java.util.ArrayList.new", w, 0);
                writeWithIndentationAndNewLine(memberPrefix + member + ".each do |m|", w, 0);
                writeConversionToJava("m", returnValueGeneric, null, allClasses, w, INDENT_LEVEL, memberPrefix);
                writeWithIndentationAndNewLine("tmp.add(m)", w, 0);
                writeWithIndentationAndNewLine("end", w, -INDENT_LEVEL);
                writeWithIndentationAndNewLine(memberPrefix + member + " = tmp", w, 0);
            } else {
                // At this point if we can't find the class we throw
                final ClassEnumOrInterface classEnumOrIfce = findClassEnumOrInterface(returnValueType, allClasses);
                if (classEnumOrIfce.isEnum()) {
                    writeWithIndentationAndNewLine(memberPrefix + member + " = Java::" + classEnumOrIfce.getFullName() + ".value_of(\"#{" + memberPrefix + member + ".to_s}\") unless "  + memberPrefix + member + ".nil?", w, 0);
                } else {
                    writeWithIndentationAndNewLine(memberPrefix + member + " = " + memberPrefix + member + ".to_java unless "  + memberPrefix + member + ".nil?", w, 0);
                }
            }
        }
    }


    private void dedupPreserveOrder(final List<Method> flattenedMethods) {

        final TreeSet<String> currentMethodNames = new TreeSet<String>();
        final Iterator<Method> it = flattenedMethods.iterator();
        while (it.hasNext()) {
            final String methodName = it.next().getName();
            if (currentMethodNames.contains(methodName)) {
                it.remove();
                continue;
            }
            currentMethodNames.add(methodName);
        }
    }

    private void generateAttributeAccessorFromGetterMethods(final ClassEnumOrInterface obj, final List<Method> flattenedMethods, final Writer w) throws IOException, GeneratorException {
        boolean first = true;
        writeWithIndentation("attr_accessor ", w, obj.isInterface() ? 0 : INDENT_LEVEL);
        for (final Method m : flattenedMethods) {
            if (!m.isGetter()) {
                continue;
            }
            final String member = camelToUnderscore(convertGetterMethodToFieldName(m.getName()));
            final String baseString = ":" + member;
            if (!first) {
                writeAppend(", ", w);
            }
            writeAppend(baseString, w);
            first = false;
        }
        writeNewLine(w);
        writeNewLine(w);
    }

    private String convertGetterMethodToFieldName(final String input) throws GeneratorException {
        if (input.startsWith("get")) {
            return input.substring(3);
        } else if (input.startsWith("is")) {
            //return input.substring(2);
            return input;
        }
        throw new GeneratorException("Unexpected getter method :" + input);
    }

    @Override
    protected String createFileName(final String name, final boolean withExtension) {
        final String extension = withExtension ? ".rb" : "";
        return camelToUnderscore(name + extension);
    }

    @Override
    protected String getRequirePrefix() {
        return REQUIRE_PREFIX;
    }

    @Override
    protected String getRequireFileName() {
        return REQUIRE_FILE_NAME;
    }

    @Override
    protected void completeGeneration(final List<ClassEnumOrInterface> classes, final File outputDir) throws GeneratorException {
        generateRubyRequireFile(classes, outputDir);
    }

    @Override
    protected String getLicense() {
        return LICENSE_NAME;
    }

    private List<Method> getTopMethods(final ClassEnumOrInterface obj, final List<ClassEnumOrInterface> allClasses) throws GeneratorException {
        if (obj.isEnum()) {
            return Collections.emptyList();
        }
        final List<Method> result = new ArrayList<Method>();
        if (obj.isClass()) {
            getMethodsFromExtendedClasses(obj, allClasses, result);
        } else if (obj.isInterface()) {
            getMethodsFromExtendedInterfaces(obj, allClasses, result);
        } else {
            throw new GeneratorException("Unexpected obj with no class/enum/interface:" + obj.getName());
        }
        return result;
    }

    private void getMethodsFromExtendedInterfaces(final ClassEnumOrInterface obj, final List<ClassEnumOrInterface> allClasses, final List<Method> result) throws GeneratorException {
        // Reverse list to match original algorithm from ruby parser
        final List<String> superInterfaces = Lists.reverse(obj.getSuperInterfaces());
        for (final String cur : superInterfaces) {
            // Don't expect to find those in our packages
            if (cur.startsWith("java.lang")) {
                continue;
            }

            final ClassEnumOrInterface ifce = findClassEnumOrInterface(cur, allClasses);
            result.addAll(ifce.getMethods());
            getMethodsFromExtendedInterfaces(ifce, allClasses, result);
        }
    }

    private void getMethodsFromExtendedClasses(final ClassEnumOrInterface obj, final List<ClassEnumOrInterface> allClasses, final List<Method> result) throws GeneratorException {
        final String superBaseClass = obj.getSuperBaseClass();
        if (superBaseClass == null) {
            return;
        }
        // Don't expect to find those in our packages
        if (superBaseClass.startsWith("java.lang")) {
            return;
        }
        final ClassEnumOrInterface superClass = findClassEnumOrInterface(superBaseClass, allClasses);
        result.addAll(superClass.getMethods());
        getMethodsFromExtendedClasses(superClass, allClasses, result);
    }

    private ClassEnumOrInterface findClassEnumOrInterface(final String fullyQualifiedName, final List<ClassEnumOrInterface> allClasses) throws GeneratorException {
        for (final ClassEnumOrInterface cur : allClasses) {
            if (cur.getFullName().equals(fullyQualifiedName)) {
                return cur;
            }
        }
        throw new GeneratorException("Cannot find classEnumOrInterface " + fullyQualifiedName);
    }
}