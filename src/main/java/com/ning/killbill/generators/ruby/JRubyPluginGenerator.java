package com.ning.killbill.generators.ruby;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.io.Resources;
import com.ning.killbill.KillbillListener;
import com.ning.killbill.com.ning.killbill.args.KillbillParserArgs.GENERATOR_MODE;
import com.ning.killbill.generators.GeneratorException;
import com.ning.killbill.objects.Annotation;
import com.ning.killbill.objects.ClassEnumOrInterface;
import com.ning.killbill.objects.Field;
import com.ning.killbill.objects.MethodOrDecl;
import com.ning.killbill.objects.Type;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.TreeSet;

import javax.annotation.Nullable;

public class JRubyPluginGenerator extends RubyBaseGenerator {

    private final static String LICENSE_NAME = "RubyLicense.txt";
    private final static int INDENT_LEVEL = 2;
    private static final String REQUIRE_API_PREFIX = "killbill/gen/api";
    private static final String REQUIRE_PLUGIN_API_PREFIX = "killbill/gen/plugin-api";

    private final String[] POJO_MODULES = {"Killbill", "Plugin", "Model"};
    private final String[] API_MODULES = {"Killbill", "Plugin", "Api"};

    private final String NOTIFICATION_PLUGIN_API = "NotificationPluginApi";
    private final String JPLUGIN = "JPlugin";
    private final String UNDEFINED_GENERIC = "Undefined";

    private static final List<ClassEnumOrInterface> STATICALLY_API_GENERATED_CLASSES = ImmutableList.<ClassEnumOrInterface>builder()
            .add(new ClassEnumOrInterface("EnumeratorIterator", ClassEnumOrInterface.ClassEnumOrInterfaceType.CLASS, null, null, false))
            .build();

    // Only plugged for Method return value
    private static final List<String> SKIP_NOT_IMPLEMENTED_TYPE = ImmutableList.<String>builder()
            .add("org.joda.time.Period")
            .build();


    private final List<ClassEnumOrInterface> staticallyGeneratedClasses;

    public JRubyPluginGenerator() {
        super();
        this.staticallyGeneratedClasses = new ArrayList<ClassEnumOrInterface>();
    }


    private void generateStaticClass(final String className, final File outputDir) throws GeneratorException {

        OutputStream out = null;
        try {
            final String resourceName = createFileName(className, true);
            final URL classUrl = Resources.getResource(resourceName);

            final File output = new File(outputDir, resourceName);

            out = new FileOutputStream(output);
            Resources.copy(classUrl, out);
        } catch (IOException e) {
            throw new GeneratorException("Failed to generate file " + className, e);
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException ignore) {
                }
            }
        }
    }



    @Override
    protected void generateClass(final ClassEnumOrInterface obj, final List<ClassEnumOrInterface> allClasses, final File outputDir, final GENERATOR_MODE mode) throws GeneratorException {

        resetIndentation();

        final File output = new File(outputDir, createFileName(obj.getName(), true));
        writeLicense(output);


        Writer w = null;
        try {
            w = new FileWriter(output, true);

            writeHeader(w);

            final boolean isInterface = obj.isInterface();
            final boolean isApi = isInterface && isApiFile(obj.getName());

            final boolean isPureNotificationPluginApi = isApi && obj.getName().equals(NOTIFICATION_PLUGIN_API);

            if (mode == GENERATOR_MODE.JRUBY_PLUGIN_API && isApi && !isPureNotificationPluginApi) {
                writeWithIndentationAndNewLine("require 'killbill/gen/plugin-api/notification_plugin_api'", w, 0);
            }

            generateStartModules(w, isApi);

            final List<MethodOrDecl> flattenedMethods = new ArrayList<MethodOrDecl>();
            flattenedMethods.addAll(getTopMethods(obj, allClasses));
            flattenedMethods.addAll(obj.getMethodOrDecls());

            dedupPreserveOrder(flattenedMethods);

            writeNewLine(w);

            int curIndent = INDENT_LEVEL;
            if (isInterface) {
                writeWithIndentationAndNewLine("java_package '" + obj.getPackageName() + "'", w, curIndent);
                curIndent = 0;
            }

            if (mode == GENERATOR_MODE.JRUBY_PLUGIN_API && isApi) {
                final String baseClass = isPureNotificationPluginApi ? JPLUGIN : NOTIFICATION_PLUGIN_API;
                writeWithIndentationAndNewLine("class " + obj.getName() + " < " + baseClass, w, curIndent);
            } else {
                writeWithIndentationAndNewLine("class " + obj.getName(), w, curIndent);
            }
            writeNewLine(w);

            if (isInterface) {
                writeWithIndentationAndNewLine("include " + obj.getPackageName() + "." + obj.getName(), w, INDENT_LEVEL);
            }
            writeNewLine(w);

            if (isApi) {
                generateForKillbillApi(obj, w, flattenedMethods, mode);
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


    private void generateForKillbillApi(final ClassEnumOrInterface obj, final Writer w, final List<MethodOrDecl> flattenedMethods, GENERATOR_MODE mode) throws IOException, GeneratorException {


        if (mode == GENERATOR_MODE.JRUBY_API) {
            generateApiInitializeMethod(w);
        } else if (mode == GENERATOR_MODE.JRUBY_PLUGIN_API) {
            generatePluginApiInitializeMethod(w);
        }

        for (final MethodOrDecl m : flattenedMethods) {
            final String methodName = generateMethodSignature(w, m);
            generateMethodArgumentConversion(w, mode, m);
            if (mode == GENERATOR_MODE.JRUBY_API) {
                generateApiMethodReturnConversion(w, m, methodName);
            } else if (mode == GENERATOR_MODE.JRUBY_PLUGIN_API) {
                generatePluginApiMethodReturnConversion(w, m, methodName);
            }
            writeWithIndentationAndNewLine("end", w, -INDENT_LEVEL);
        }
    }

    private void generatePluginApiInitializeMethod(final Writer w) throws IOException {
        writeWithIndentationAndNewLine("def initialize(real_class_name, services = {})", w, 0);
        writeWithIndentationAndNewLine("super(real_class_name, services)", w, INDENT_LEVEL);
        writeWithIndentationAndNewLine("end", w, -INDENT_LEVEL);
        writeNewLine(w);
    }

    private void generateApiInitializeMethod(final Writer w) throws IOException {
        writeWithIndentationAndNewLine("def initialize(real_java_api)", w, 0);
        writeWithIndentationAndNewLine("@real_java_api = real_java_api", w, INDENT_LEVEL);
        writeWithIndentationAndNewLine("end", w, -INDENT_LEVEL);
        writeNewLine(w);
    }

    private void generatePluginApiMethodReturnConversion(final Writer w, final MethodOrDecl m, final String methodName) throws IOException, GeneratorException {


        final boolean isVoidReturn = "void".equals(m.getReturnValueType().getBaseType());
        writeWithIndentationAndNewLine("begin", w, 0);
        if (isVoidReturn) {
            writeWithIndentation("@delegate_plugin." + methodName + "(", w, INDENT_LEVEL);
        } else {
            writeWithIndentation("res = @delegate_plugin." + methodName + "(", w, INDENT_LEVEL);
        }

        boolean first = true;
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
            writeConversionToJava("res", m.getReturnValueType(), allClasses, w, 0, "");
            writeWithIndentationAndNewLine("return res", w, 0);
        }

        writeWithIndentationAndNewLine("rescue Exception => e", w, -INDENT_LEVEL);
        writeWithIndentationAndNewLine("message = \"Failure in " + methodName + ": #{e}\"", w, +INDENT_LEVEL);
        writeWithIndentationAndNewLine("unless e.backtrace.nil?", w, +0);
        writeWithIndentationAndNewLine("message = \"#{message}\\n#{e.backtrace.join(\"\\n\")}\"", w, +INDENT_LEVEL);
        writeWithIndentationAndNewLine("end", w, +-INDENT_LEVEL);
        writeWithIndentationAndNewLine("logger.warn message", w, +0);
        writeWithIndentationAndNewLine("raise Java::org.killbill.billing.payment.plugin.api.PaymentPluginApiException.new(\"" + methodName + " failure\", e.message)", w, 0);
        writeWithIndentationAndNewLine("ensure", w, -INDENT_LEVEL);
        writeWithIndentationAndNewLine("@delegate_plugin.after_request", w, INDENT_LEVEL);
        writeWithIndentationAndNewLine("end", w, -INDENT_LEVEL);
    }

    private void generateApiMethodReturnConversion(final Writer w, final MethodOrDecl m, final String methodName) throws IOException, GeneratorException {
        final boolean isVoidReturn = "void".equals(m.getReturnValueType().getBaseType());
        final boolean gotExceptions = (m.getExceptions().size() > 0);
        if (gotExceptions) {
            writeWithIndentationAndNewLine("begin", w, 0);
        }
        if (isVoidReturn) {
            writeWithIndentation("@real_java_api." + methodName + "(", w, gotExceptions ? INDENT_LEVEL : 0);
        } else {
            writeWithIndentation("res = @real_java_api." + methodName + "(", w, gotExceptions ? INDENT_LEVEL : 0);
        }

        boolean first = true;
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
            writeConversionToRuby(null, "res", m.getReturnValueType(), allClasses, w, 0, false);
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
    }

    private void generateMethodArgumentConversion(final Writer w, final GENERATOR_MODE mode, final MethodOrDecl m) throws GeneratorException, IOException {

        boolean firstArg = true;
        for (Field f : m.getOrderedArguments()) {
            writeNewLine(w);
            int curIndentLevel = firstArg ? INDENT_LEVEL : 0;
            if (mode == GENERATOR_MODE.JRUBY_API) {
                writeConversionToJava(f.getName(), f.getType(), allClasses, w, curIndentLevel, "");
            } else if (mode == GENERATOR_MODE.JRUBY_PLUGIN_API) {
                writeConversionToRuby(null, f.getName(), f.getType(), allClasses, w, curIndentLevel, false);
            }
            firstArg = false;
        }
    }

    private String generateMethodSignature(final Writer w, final MethodOrDecl m) throws IOException {

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

        final String methodName = camelToUnderscore(m.getName());
        writeWithIndentation("def " + methodName + "(", w, 0);
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
        return methodName;
    }

    private boolean isApiFile(final String fileName) {
        return fileName.endsWith("Api");
    }

    private void generateForPojo(final ClassEnumOrInterface obj, final Writer w, final List<MethodOrDecl> flattenedMethods) throws IOException, GeneratorException {
        generateAttributeAccessorFromGetterMethods(obj, flattenedMethods, w);

        writeWithIndentationAndNewLine("def initialize()", w, 0);
        writeWithIndentationAndNewLine("end", w, 0);
        writeNewLine(w);

        generateToJava(obj, flattenedMethods, w);

        generateToRuby(obj, flattenedMethods, w);
    }


    private void generateToRuby(final ClassEnumOrInterface obj, final List<MethodOrDecl> flattenedMethods, final Writer w) throws IOException, GeneratorException {
        boolean first = true;
        writeWithIndentationAndNewLine("def to_ruby(j_obj)", w, 0);
        for (final MethodOrDecl m : flattenedMethods) {
            if (!m.isGetter()) {
                continue;
            }
            if (first) {
                writeConversionToRuby(obj, m, allClasses, w, INDENT_LEVEL);
                first = false;
            } else {
                writeNewLine(w);
                writeConversionToRuby(obj, m, allClasses, w, 0);
            }
        }
        writeWithIndentationAndNewLine("self", w, 0);
        writeWithIndentationAndNewLine("end", w, -INDENT_LEVEL);
        writeNewLine(w);
    }

    private void writeConversionToRuby(@Nullable final ClassEnumOrInterface obj, final MethodOrDecl m, final List<ClassEnumOrInterface> allClasses, final Writer w, int indentOffset) throws GeneratorException, IOException {
        final String member = camelToUnderscore(convertGetterMethodToFieldName(m.getName()));
        writeConversionToRuby(obj, member, m.getReturnValueType(), allClasses, w, indentOffset, true);
    }


    private void writeConversionToRuby(@Nullable final ClassEnumOrInterface obj, final String member, final String returnValueType, final String returnValueGeneric, final List<ClassEnumOrInterface> allClasses, final Writer w, int indentOffset, boolean fromJobj) throws GeneratorException, IOException {
        writeConversionToRuby(obj, member, new Type(returnValueType, returnValueGeneric), allClasses, w, indentOffset, fromJobj);
    }

    private void writeConversionToRuby(@Nullable final ClassEnumOrInterface obj, final String member, final Type type, final List<ClassEnumOrInterface> allClasses, final Writer w, int indentOffset, boolean fromJobj) throws GeneratorException, IOException {
        writeConversionToRuby(obj, member, type, allClasses, w, indentOffset, fromJobj, 0);
    }
    private void writeConversionToRuby(@Nullable final ClassEnumOrInterface obj, final String member, final Type type, final List<ClassEnumOrInterface> allClasses, final Writer w, int indentOffset, boolean fromJobj, int depth) throws GeneratorException, IOException {
        final String returnValueType = type.getBaseType();
        final String returnValueGeneric = type.getGenericType();

        if (SKIP_NOT_IMPLEMENTED_TYPE.contains(returnValueType)) {
            System.err.println("SKIPPING METHOD " + member + ": Return value type has not been implemented " + returnValueType);
            return;
        }

        writeWithIndentationAndNewLine("# conversion for " + member + " [type = " + type.toString() + "]", w, indentOffset);

        // See https://github.com/killbill/killbill-java-parser/issues/6 and https://github.com/killbill/killbill-java-parser/issues/7
        final boolean shouldIgnore = obj != null && (("plan".equals(member) && "PlanPhase".equals(obj.getName())) ||
                                                     ("min_top_up_credit".equals(member) && "TieredBlock".equals(obj.getName())));
        final String linePrefix = shouldIgnore ? "#" : "";

        final String memberPrefix = fromJobj ? "@" : "";
        if (fromJobj) {
            // See https://github.com/killbill/killbill-java-parser/issues/5
            final String hackedMember = member.equals("included") && "org.killbill.billing.catalog.api.Product[]".equals(type.toString()) ? "get_" + member : member;
            writeWithIndentationAndNewLine(linePrefix + memberPrefix + member + " = j_obj." + hackedMember, w, 0);
        }

        if (returnValueType.equals("byte")) {
        } else if (returnValueType.equals("short") || returnValueType.equals("java.lang.Short")) {
        } else if (returnValueType.equals("int") || returnValueType.equals("java.lang.Integer")) {
        } else if (returnValueType.equals("long") || returnValueType.equals("java.lang.Long")) {
        } else if (returnValueType.equals("float") || returnValueType.equals("java.lang.Float")) {
        } else if (returnValueType.equals("double") || returnValueType.equals("java.lang.Double")) {
        } else if (returnValueType.equals("char") || returnValueType.equals("java.lang.Char")) {
        } else {
            final String tmp = depth == 0 ? "tmp" : "tmp" + depth;
            if (returnValueType.equals("boolean") || returnValueType.equals("java.lang.Boolean")) {
                writeWithIndentationAndNewLine("if " + memberPrefix + member + ".nil?", w, 0);
                writeWithIndentationAndNewLine(memberPrefix + member + " = false", w, INDENT_LEVEL);
                writeWithIndentationAndNewLine("else", w, -INDENT_LEVEL);
                writeWithIndentationAndNewLine(tmp + "_bool = (" + memberPrefix + member + ".java_kind_of? java.lang.Boolean) ? " + memberPrefix + member + ".boolean_value : " + memberPrefix + member, w, INDENT_LEVEL);
                writeWithIndentationAndNewLine(memberPrefix + member + " = " + tmp + "_bool ? true : false", w, 0);
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
                    writeWithIndentationAndNewLine(memberPrefix + member + " = " + memberPrefix + member + ".nil? ? 0 : BigDecimal.new(" + memberPrefix + member + ".to_s)", w, 0);
                } else if ("org.joda.time.DateTime".equals(returnValueType) ||
                        "java.util.Date".equals(returnValueType)) {
                    writeWithIndentationAndNewLine("if !" + memberPrefix + member + ".nil?", w, 0);
                    if ("java.util.Date".equals(returnValueType)) {
                        // First convert to DateTime
                        writeWithIndentationAndNewLine(memberPrefix + member + " = " + "Java::org.joda.time.DateTime.new(" + memberPrefix + member + ")", w, INDENT_LEVEL);
                    }
                    writeWithIndentationAndNewLine("fmt = Java::org.joda.time.format.ISODateTimeFormat.date_time_no_millis # See https://github.com/killbill/killbill-java-parser/issues/3", w, ("java.util.Date".equals(returnValueType) ? 0 : INDENT_LEVEL));
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
                        Type.ARRAY.equals(returnValueType)) {
                    writeWithIndentationAndNewLine(tmp + " = []", w, 0);
                    writeWithIndentationAndNewLine("(" + memberPrefix + member + " || []).each do |m|", w, 0);
                    writeConversionToRuby(obj, "m", returnValueGeneric, null, allClasses, w, INDENT_LEVEL, false);
                    writeWithIndentationAndNewLine(tmp + " << m", w, 0);
                    writeWithIndentationAndNewLine("end", w, -INDENT_LEVEL);
                    writeWithIndentationAndNewLine(memberPrefix + member + " = " + tmp, w, 0);
                } else if ("java.lang.Iterable".equals(returnValueType)) {
                    writeWithIndentationAndNewLine(tmp + " = []", w, 0);
                    writeWithIndentationAndNewLine("(" + memberPrefix + member + ".nil? ? [] : " + memberPrefix + member + ".iterator).each do |m|", w, 0);
                    writeConversionToRuby(obj, "m", returnValueGeneric, null, allClasses, w, INDENT_LEVEL, false);
                    writeWithIndentationAndNewLine(tmp + " << m", w, 0);
                    writeWithIndentationAndNewLine("end", w, -INDENT_LEVEL);
                    writeWithIndentationAndNewLine(memberPrefix + member + " = " + tmp, w, 0);
                } else if ("java.util.Iterator".equals(returnValueType)) {
                    // TODO
                    // Leave default where ruby can call next, not ideal because that would break pure ruby plugin code
                } else if ("java.util.Map".equals(returnValueType)) {
                    writeWithIndentationAndNewLine(tmp + " = {}", w, 0);
                    final String jtmp = "j" + tmp + depth;
                    writeWithIndentationAndNewLine(jtmp + " = " + memberPrefix + member + " || java.util.HashMap.new", w, 0);
                    writeWithIndentationAndNewLine(jtmp + ".key_set.each do |k|", w, 0);
                    if (type.getGenericSubTypes() != null && type.getGenericSubTypes().size() == 2) {
                        writeConversionToRuby(obj, "k", type.getGenericSubTypes().get(0), allClasses, w, INDENT_LEVEL, false, depth + 1);
                        writeWithIndentationAndNewLine("v = " + jtmp + ".get(k)", w, 0);
                        writeConversionToRuby(obj, "v", type.getGenericSubTypes().get(1), allClasses, w, INDENT_LEVEL, false, depth + 1);
                    } else {
                        // No generic information? Should we bail or trust JRuby to do the right thing?
                        writeWithIndentationAndNewLine("v = " + "j" + tmp + depth + ".get(k)", w, 0);
                    }
                    writeWithIndentationAndNewLine(tmp + "[k] = v", w, 0);
                    writeWithIndentationAndNewLine("end", w, -INDENT_LEVEL);
                    writeWithIndentationAndNewLine(memberPrefix + member + " = " + tmp, w, -INDENT_LEVEL);
                } else if (UNDEFINED_GENERIC.equals(returnValueType)) {
                    // generic type; leave it as is
                } else {
                    final ClassEnumOrInterface classEnumOrInterface = findClassEnumOrInterface(returnValueType, allClasses);
                    if (classEnumOrInterface.isEnum()) {
                        writeWithIndentationAndNewLine(memberPrefix + member + " = " + memberPrefix + member + ".to_s.to_sym unless " + memberPrefix + member + ".nil?", w, 0);
                    } else {
                        writeWithIndentationAndNewLine(linePrefix + memberPrefix + member + " = " + getJrubyPoJo(returnValueType) + ".new.to_ruby(" + memberPrefix + member + ") unless " + memberPrefix + member + ".nil?", w, 0);
                    }
                }
            }
        }
    }


    private String getJrubyPoJo(final String pojoBaseName) throws GeneratorException {
        String[] parts = pojoBaseName.split("\\.");
        final String jrubyObject = Joiner.on("::").join(POJO_MODULES) + "::" + parts[parts.length - 1];
        return jrubyObject;
    }

    private void generateToJava(final ClassEnumOrInterface obj, final List<MethodOrDecl> flattenedMethods, final Writer w) throws IOException, GeneratorException {
        boolean first = true;
        writeWithIndentationAndNewLine("def to_java()", w, 0);
        for (final MethodOrDecl m : flattenedMethods) {
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
        //
        // If this is a class, that sucks because we can't rely on the jruby layer to map the Jruby object to a valid java object based on the include mechanism
        // so we new to explicitely call the CTOR of that class.
        // TODO There is a hack here we assumes that CTOR takes all the fields as arguments is the correct order. We should really fix our API to not have those cases
        // or be smarter in the conversion process and loo at the CTOR arguments into more details
        if (obj.isClass()) {
            writeWithIndentation("Java::" + obj.getPackageName() + "." + obj.getName() + ".new(", w, 0);
            first = true;
            for (final MethodOrDecl m : flattenedMethods) {
                if (!m.isGetter()) {
                    continue;
                }
                if (!first) {
                    writeAppend(", ", w);
                }
                final String member = camelToUnderscore(convertGetterMethodToFieldName(m.getName()));
                writeAppend("@" + member, w);
                first = false;
            }
            writeAppend(")", w);
            writeNewLine(w);
        } else {
            writeWithIndentationAndNewLine("self", w, 0);
        }
        writeWithIndentationAndNewLine("end", w, -INDENT_LEVEL);
        writeNewLine(w);
    }


    private void writeConversionToJava(final MethodOrDecl m, final List<ClassEnumOrInterface> allClasses, final Writer w, int indentOffset) throws GeneratorException, IOException {
        final String member = camelToUnderscore(convertGetterMethodToFieldName(m.getName()));
        writeConversionToJava(member, m.getReturnValueType(), allClasses, w, indentOffset, "@");
    }

    private void writeConversionToJava(final String member, final String returnValueType, final String returnValueGeneric, final List<ClassEnumOrInterface> allClasses, final Writer w, int indentOffset, String memberPrefix) throws GeneratorException, IOException {
        writeConversionToJava(member, new Type(returnValueType, returnValueGeneric), allClasses, w, indentOffset, memberPrefix);
    }

    private void writeConversionToJava(final String member, final Type type, final List<ClassEnumOrInterface> allClasses, final Writer w, int indentOffset, String memberPrefix) throws GeneratorException, IOException {
        writeConversionToJava(member, type, allClasses, w, indentOffset, memberPrefix, 0);
    }


    private void writeConversionToJava(final String member, final Type type, final List<ClassEnumOrInterface> allClasses, final Writer w, int indentOffset, String memberPrefix, int depth) throws GeneratorException, IOException {
        final String returnValueType = type.getBaseType();
        final String returnValueGeneric = type.getGenericType();

        if (SKIP_NOT_IMPLEMENTED_TYPE.contains(returnValueType)) {
            return;
        }

        writeWithIndentationAndNewLine("# conversion for " + member + " [type = " + type.toString() + "]", w, indentOffset);
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
                writeWithIndentationAndNewLine(memberPrefix + member + " = " + memberPrefix + member + ".to_s unless " + memberPrefix + member + ".nil?", w, 0);
            } else if ("java.util.UUID".equals(returnValueType)) {
                writeWithIndentationAndNewLine(memberPrefix + member + " = java.util.UUID.fromString(" + memberPrefix + member + ".to_s) unless " + memberPrefix + member + ".nil?", w, 0);
            } else if ("java.math.BigDecimal".equals(returnValueType)) {
                writeWithIndentationAndNewLine("if " + memberPrefix + member + ".nil?", w, 0);
                writeWithIndentationAndNewLine(memberPrefix + member + " = java.math.BigDecimal::ZERO", w, INDENT_LEVEL);
                writeWithIndentationAndNewLine("else", w, -INDENT_LEVEL);
                //writeWithIndentationAndNewLine(member + " = java.math.BigDecimal.new(" + member + ".respond_to?(:cents) ? " + member + ".cents : " + member + ".to_i)", w, INDENT_LEVEL);
                writeWithIndentationAndNewLine(memberPrefix + member + " = java.math.BigDecimal.new(" + memberPrefix + member + ".to_s)", w, INDENT_LEVEL);
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
                writeWithIndentationAndNewLine(memberPrefix + member + " = Java::org.joda.time.DateTimeZone.forID((" + memberPrefix + member + ".respond_to?(:identifier) ? " + memberPrefix + member + ".identifier : " + memberPrefix + member + ".to_s))", w, INDENT_LEVEL);
                writeWithIndentationAndNewLine("end", w, -INDENT_LEVEL);
            } else {
                final String tmp = depth == 0 ? "tmp" : "tmp" + depth;
                if (Type.ARRAY.equals(returnValueType)) {
                    //
                    // We have to use a strategy a bit different here to avoid getting TypeError (coerce). Instead of adding elements into a
                    // java.util.ArrayList, we use a ruby array, and that allows us to use the 'to_java method with the Java type' as indicated
                    // in the doc (https://github.com/jruby/jruby/wiki/CallingJavaFromJRuby#arrays)
                    //
                    writeWithIndentationAndNewLine(tmp + " = []", w, 0);
                    writeWithIndentationAndNewLine("(" + memberPrefix + member + " || []).each do |m|", w, 0);
                    writeConversionToJava("m", returnValueGeneric, null, allClasses, w, INDENT_LEVEL, "");
                    writeWithIndentationAndNewLine(tmp + " << m", w, 0);
                    writeWithIndentationAndNewLine("end", w, -INDENT_LEVEL);
                    writeWithIndentationAndNewLine(memberPrefix + member + " = " + tmp + ".to_java Java::" + returnValueGeneric, w, 0);
                } else if ("java.util.List".equals(returnValueType) ||
                           "java.util.Collection".equals(returnValueType) ||
                           "java.lang.Iterable".equals(returnValueType)) {
                    writeWithIndentationAndNewLine(tmp + " = java.util.ArrayList.new", w, 0);
                    writeWithIndentationAndNewLine("(" + memberPrefix + member + " || []).each do |m|", w, 0);
                    writeConversionToJava("m", returnValueGeneric, null, allClasses, w, INDENT_LEVEL, "");
                    writeWithIndentationAndNewLine(tmp + ".add(m)", w, 0);
                    writeWithIndentationAndNewLine("end", w, -INDENT_LEVEL);
                    writeWithIndentationAndNewLine(memberPrefix + member + " = " + tmp, w, 0);
                } else if ("java.util.Set".equals(returnValueType) ||
                        ("java.util.SortedSet".equals(returnValueType))) {
                    writeWithIndentationAndNewLine(tmp + " = java.util.TreeSet.new", w, 0);
                    writeWithIndentationAndNewLine("(" + memberPrefix + member + " || []).each do |m|", w, 0);
                    writeConversionToJava("m", returnValueGeneric, null, allClasses, w, INDENT_LEVEL, "");
                    writeWithIndentationAndNewLine(tmp + ".add(m)", w, 0);
                    writeWithIndentationAndNewLine("end", w, -INDENT_LEVEL);
                    writeWithIndentationAndNewLine(memberPrefix + member + " = " + tmp, w, 0);
                } else if ("java.util.Iterator".equals(returnValueType)) {
                    writeWithIndentationAndNewLine(memberPrefix + member + " = Killbill::Plugin::Model::EnumeratorIterator.new(" + memberPrefix + member + ")", w, 0);
                } else if ("java.util.Map".equals(returnValueType)) {
                    writeWithIndentationAndNewLine(tmp + " = java.util.HashMap.new", w, 0);
                    writeWithIndentationAndNewLine("(" + memberPrefix + member + " || {}).each do |k,v|", w, 0);
                    if (type.getGenericSubTypes() != null && type.getGenericSubTypes().size() == 2) {
                        writeConversionToJava("k", type.getGenericSubTypes().get(0), allClasses, w, INDENT_LEVEL, "", depth + 1);
                        writeConversionToJava("v", type.getGenericSubTypes().get(1), allClasses, w, INDENT_LEVEL, "", depth + 1);
                    } else {
                        // No generic information? Should we bail or trust JRuby to do the right thing?
                    }
                    writeWithIndentationAndNewLine(tmp + ".put(k, v)", w, 0);
                    writeWithIndentationAndNewLine("end", w, -INDENT_LEVEL);
                    writeWithIndentationAndNewLine(memberPrefix + member + " = " + tmp, w, -INDENT_LEVEL);
                } else if (UNDEFINED_GENERIC.equals(returnValueType)) {
                    // generic type; leave it as is
                } else {
                    final ClassEnumOrInterface classEnumOrIfce = findClassEnumOrInterface(returnValueType, allClasses);
                    if (classEnumOrIfce.isEnum()) {
                        writeWithIndentationAndNewLine(memberPrefix + member + " = " + enumValueOf(classEnumOrIfce, member, memberPrefix) + " unless " + memberPrefix + member + ".nil?", w, 0);
                    } else {
                        writeWithIndentationAndNewLine(memberPrefix + member + " = " + memberPrefix + member + ".to_java unless " + memberPrefix + member + ".nil?", w, 0);
                    }
                }
            }
        }
    }

    private static CharSequence enumValueOf(final ClassEnumOrInterface enumType, final String member, final String memberPrefix) {
        // e.g. Java::org.killbill.billing.catalog.api.Currency.value_of( @currency.to_s )
        return "Java::" + enumType.getFullName() + ".value_of( " + memberPrefix + member + ".to_s )";
    }

    private void dedupPreserveOrder(final List<MethodOrDecl> flattenedMethods) {

        final TreeSet<String> currentMethodNames = new TreeSet<String>();
        final Iterator<MethodOrDecl> it = flattenedMethods.iterator();
        while (it.hasNext()) {
            final String methodName = it.next().getName();
            if (currentMethodNames.contains(methodName)) {
                it.remove();
                continue;
            }
            currentMethodNames.add(methodName);
        }
    }

    private void generateAttributeAccessorFromGetterMethods(final ClassEnumOrInterface obj, final List<MethodOrDecl> flattenedMethods, final Writer w) throws IOException, GeneratorException {
        boolean first = true;
        writeWithIndentation("attr_accessor ", w, obj.isInterface() ? 0 : INDENT_LEVEL);
        for (final MethodOrDecl m : flattenedMethods) {
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
            return input;
        } else {
            // Some method ended there but don't seem to really be getters (e.g iterator), we just return them 'as is'
            return input;
        }
    }

    @Override
    protected String createFileName(final String name, final boolean withExtension) {
        final String extension = withExtension ? ".rb" : "";
        return camelToUnderscore(name + extension);
    }

    @Override
    protected String getRequirePrefix(final GENERATOR_MODE mode) throws GeneratorException {
        if (mode == GENERATOR_MODE.JRUBY_API) {
            return REQUIRE_API_PREFIX;
        } else if (mode == GENERATOR_MODE.JRUBY_PLUGIN_API) {
            return REQUIRE_PLUGIN_API_PREFIX;
        } else {
            throw new GeneratorException("Unexpected mode  :" + mode);
        }
    }

    @Override
    protected String getRequireFileName() {
        return REQUIRE_FILE_NAME;
    }

    @Override
    protected void startGeneration(final List<ClassEnumOrInterface> classes, final File outputDir, final GENERATOR_MODE mode) throws GeneratorException {
        switch (mode) {
            case JRUBY_API:
                staticallyGeneratedClasses.addAll(STATICALLY_API_GENERATED_CLASSES);
                break;
            case JRUBY_PLUGIN_API:
            case NON_APPLICABLE:
            default:
                break;
        }
        for (ClassEnumOrInterface cur : staticallyGeneratedClasses) {
            generateStaticClass(cur.getName(), outputDir);
        }
    }

    @Override
    protected void completeGeneration(final List<ClassEnumOrInterface> classes, final File outputDir, final GENERATOR_MODE mode) throws GeneratorException {
        classes.addAll(staticallyGeneratedClasses);
        generateRubyRequireFile(classes, outputDir, mode);
    }

    @Override
    protected String getLicense() {
        return LICENSE_NAME;
    }

    private List<MethodOrDecl> getTopMethods(final ClassEnumOrInterface obj, final List<ClassEnumOrInterface> allClasses) throws GeneratorException {
        if (obj.isEnum()) {
            return Collections.emptyList();
        }
        final List<MethodOrDecl> result = new ArrayList<MethodOrDecl>();
        if (obj.isClass()) {
            getMethodsFromExtendedClasses(obj, allClasses, result);
        } else if (obj.isInterface()) {
            getMethodsFromExtendedInterfaces(obj, allClasses, result);
        } else {
            throw new GeneratorException("Unexpected obj with no class/enum/interface:" + obj.getName());
        }
        return result;
    }

    private void getMethodsFromExtendedInterfaces(final ClassEnumOrInterface obj, final List<ClassEnumOrInterface> allClasses, final List<MethodOrDecl> result) throws GeneratorException {
// Reverse list to match original algorithm from ruby parser
        final List<String> superInterfaces = Lists.reverse(obj.getSuperInterfaces());
        for (final String cur : superInterfaces) {
            // Don't expect to find those in our packages
            if (cur.startsWith("java.lang") || cur.startsWith("java.io")) {
                addMissingMethodsFromJavaLang(cur, allClasses, result);
                continue;
            }

            final ClassEnumOrInterface ifce = findClassEnumOrInterface(cur, allClasses);
            result.addAll(ifce.getMethodOrDecls());
            getMethodsFromExtendedInterfaces(ifce, allClasses, result);
        }
    }

    private void getMethodsFromExtendedClasses(final ClassEnumOrInterface obj, final List<ClassEnumOrInterface> allClasses, final List<MethodOrDecl> result) throws GeneratorException {
        final String superBaseClass = obj.getSuperBaseClass();
        if (superBaseClass == null) {
            return;
        }
        // Don't expect to find those in our packages
        if (superBaseClass.startsWith("java.lang")) {
            return;
        }
        final ClassEnumOrInterface superClass = findClassEnumOrInterface(superBaseClass, allClasses);
        result.addAll(superClass.getMethodOrDecls());
        getMethodsFromExtendedClasses(superClass, allClasses, result);
    }

    //
// Since we don't parse all java classes, we need to add the methods from the java.lang classes we care about.
    private void addMissingMethodsFromJavaLang(final String javaLangBaseClass, final List<ClassEnumOrInterface> allClasses, final List<MethodOrDecl> result) throws GeneratorException {
        if (javaLangBaseClass.equals("java.lang.Iterable")) {
            final MethodOrDecl iteratorMethod = new JavaLanMethodOrDecl("iterator", new Type("java.util.Iterator", KillbillListener.UNDEFINED_GENERIC), true, null);
            result.add(iteratorMethod);
        }
    }

    private static class JavaLanMethodOrDecl extends MethodOrDecl {
        public JavaLanMethodOrDecl(final String name, final Type returnValueType, final boolean isAbstract, final List<Annotation> annotations) {
            super(name, returnValueType, isAbstract, annotations);
        }

        public boolean isGetter() {
            return true;
        }
    }
}
