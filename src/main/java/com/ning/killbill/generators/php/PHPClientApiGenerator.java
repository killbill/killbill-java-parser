package com.ning.killbill.generators.php;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import com.ning.killbill.com.ning.killbill.args.KillbillParserArgs;
import com.ning.killbill.com.ning.killbill.args.KillbillParserArgs.GENERATOR_MODE;
import com.ning.killbill.generators.BaseGenerator;
import com.ning.killbill.generators.ClientLibraryBaseGenerator;
import com.ning.killbill.generators.Generator;
import com.ning.killbill.generators.GeneratorException;
import com.ning.killbill.objects.Annotation;
import com.ning.killbill.objects.ClassEnumOrInterface;
import com.ning.killbill.objects.Constructor;
import com.ning.killbill.objects.Field;

import com.google.common.io.Resources;

public class PHPClientApiGenerator extends ClientLibraryBaseGenerator implements Generator {


    private final static String LICENSE_NAME = "PHPLicense.txt";

    private final static int INDENT_LEVEL = 4;
    private final static String DEFAULT_BASE_CLASS = "\\Killbill\\Client\\Resource";


    protected int curIndent = 0;

    public PHPClientApiGenerator() {
        super();
        resetIndentation();
    }

    protected void resetIndentation() {
        curIndent = 0;
    }


    @Override
    protected String createFileName(final String objName, boolean addPHPExtension) {
        final String extension = addPHPExtension ? ".php" : "";
        return createClassName(objName) + extension;
    }

    @Override
    protected String getRequirePrefix(final GENERATOR_MODE mode) throws GeneratorException {
        return null;
    }

    @Override
    protected String getRequireFileName() {
        return null;
    }

    @Override
    protected void generateClass(final ClassEnumOrInterface obj, final List<ClassEnumOrInterface> allClasses, final File outputDir, final GENERATOR_MODE mode) throws GeneratorException {
        final File output = new File(outputDir, createFileName(obj.getName(), true));

        writeLicense(output);

        Writer w = null;
        try {
            w = new FileWriter(output, true);

            writeNewLine(w);

            writeWithIndentationAndNewLine("namespace Killbill\\Client\\Type;", w, 0);

            writeHeader(w);

            final String baseClass = DEFAULT_BASE_CLASS;
            writeWithIndentationAndNewLine("class " + createClassName(obj.getName()) + " extends " + baseClass, w, 0);
            writeWithIndentationAndNewLine("{", w, 0);

            final Constructor ctor = getJsonCreatorCTOR(obj);
            boolean first = true;
            for (Field f : ctor.getOrderedArguments()) {
                final String attribute = getJsonPropertyAnnotationValue(obj, f);

                String type = getPHPTypeFromJavaType(f.getType().toString());
                if (type != null) {
                    type = type + "|null";
                } else {
                    type = "mixed|null";
                }
                writeWithIndentationAndNewLine("/** @var " + type + " */", w, first ? INDENT_LEVEL : 0);
                writeWithIndentationAndNewLine("protected $" + attribute + " = null;", w, 0);

                if (first) {
                    first = false;
                }
            }

            writeNewLine(w);

            for (Field f : ctor.getOrderedArguments()) {
                final String attribute = getJsonPropertyAnnotationValue(obj, f);
                final String attributeUppercased = attribute.substring(0,1).toUpperCase() + attribute.substring(1);

                String type = getPHPTypeFromJavaType(f.getType().toString());
                boolean needsTypeHint = false;

                String fullType = "mixed|null";
                if (type != null) {
                    needsTypeHint = (type.endsWith("Attributes"));
                    fullType = type + "|null";
                }

                // setter
                writeWithIndentationAndNewLine("/**", w, 0);
                writeWithIndentationAndNewLine(" * @param " + fullType + " $" + attribute, w, 0);
                writeWithIndentationAndNewLine(" */", w, 0);
                writeWithIndentationAndNewLine("public function set" + attributeUppercased + "($" + attribute + ")", w, 0);
                writeWithIndentationAndNewLine("{", w, 0);
                writeWithIndentationAndNewLine("$this->" + attribute + " = $" + attribute + ";", w, INDENT_LEVEL);
                writeWithIndentationAndNewLine("}", w, -INDENT_LEVEL);
                writeNewLine(w);

                // getter
                writeWithIndentationAndNewLine("/**", w, 0);
                writeWithIndentationAndNewLine(" * @return " + fullType, w, 0);
                writeWithIndentationAndNewLine(" */", w, 0);
                writeWithIndentationAndNewLine("public function get" + attributeUppercased + "()", w, 0);
                writeWithIndentationAndNewLine("{", w, 0);
                writeWithIndentationAndNewLine("return $this->" + attribute + ";", w, INDENT_LEVEL);
                writeWithIndentationAndNewLine("}", w, -INDENT_LEVEL);
                writeNewLine(w);

                // the type is a custom attributes class, adding a type specificer as hint for the parser
                if (needsTypeHint) {
                    writeWithIndentationAndNewLine("/**", w, 0);
                    writeWithIndentationAndNewLine(" * @return string", w, 0);
                    writeWithIndentationAndNewLine(" */", w, 0);
                    writeWithIndentationAndNewLine("public function get" + attributeUppercased + "Type()", w, 0);
                    writeWithIndentationAndNewLine("{", w, 0);
                    writeWithIndentationAndNewLine("return " + type + "::class;", w, INDENT_LEVEL);
                    writeWithIndentationAndNewLine("}", w, -INDENT_LEVEL);
                    writeNewLine(w);
                }
            }

            writeWithIndentationAndNewLine("}", w, -INDENT_LEVEL);
            w.flush();
            w.close();

        } catch (FileNotFoundException e) {
            throw new GeneratorException("Failed to generate file " + obj.getName(), e);
        } catch (IOException e) {
            throw new GeneratorException("Failed to generate file " + obj.getName(), e);
        }
    }

    @Override
    protected void completeGeneration(final List<ClassEnumOrInterface> classes, final File outputDir, final GENERATOR_MODE mode) throws GeneratorException {
    }


    @Override
    protected void startGeneration(final List<ClassEnumOrInterface> classes, final File outputDir, final GENERATOR_MODE mode) throws GeneratorException {
    }


    @Override
    protected String getLicense() {
        return LICENSE_NAME;
    }

    private static String createClassName(final String objName) {
        return objName.replace("Json", "Attributes");
    }

    protected void writeHeader(final Writer w) throws IOException {
        w.write("\n");
        w.write("/*\n");
        w.write(" * DO NOT EDIT!!!\n");
        w.write(" * File automatically generated by killbill-java-parser (git@github.com:killbill/killbill-java-parser.git)\n");
        w.write(" */\n");
        w.write("\n");
    }

    protected String getPHPTypeFromJavaType(String javaType) {
        if (javaType.equals("java.lang.String")) {
            return "string";
        }else if (javaType.equals("java.util.List")) {
            return "array";
        } else if (javaType.equals("java.util.Set")) {
            return "array";
        } else if (javaType.equals("java.util.Map")) {
            return "array";
        } else if (javaType.equals("java.lang.Boolean")) {
            return "bool";
        } else if (javaType.equals("boolean")) {
            return "bool";
        } else if (javaType.equals("java.lang.Integer")) {
            return "int";
        } else if (javaType.equals("int")) {
            return "int";
        } else if (javaType.equals("java.lang.Long")) {
            return "int"; // not sure?
        } else if (javaType.equals("java.math.BigDecimal")) {
            return "float";
        } else if (javaType.equals("java.lang.Iterable")) {
            return null; // not sure?
        }

        else if (javaType.equals("java.util.Date")) {
            return "string";
        } else if (javaType.equals("org.joda.time.LocalDate")) {
            return "string"; // not sure?
        } else if (javaType.equals("org.joda.time.DateTime")) {
            return "string"; // not sure?
        }

        else if (javaType.startsWith("org.killbill.billing.jaxrs.json.")) {
            // Converts:
            // "org.killbill.billing.jaxrs.json.PaymentMethodPluginDetailJson" => PaymentMethodPluginDetailAttributes
            // "org.killbill.billing.jaxrs.json.CatalogJson.DurationJson" => DurationAttributes
            String objName = javaType.substring(javaType.lastIndexOf(".") + 1);
            return createClassName(objName);
        }

        else {
            // Known unmatched types:
            // "org.killbill.billing.ObjectType"
            // "org.killbill.billing.catalog.api.TimeUnit"
            // "org.killbill.billing.catalog.api.BillingPeriod"
            // "org.killbill.billing.catalog.api.Currency"
            // "org.killbill.billing.catalog.api.ProductCategory"
            // "org.killbill.billing.payment.api.PaymentResponse"
            // "org.killbill.billing.overdue.api.OverdueCancellationPolicy"
            // "org.killbill.billing.entitlement.api.BlockingStateType"
            // "org.killbill.billing.util.tag.ControlTagType"
            System.err.println("Unmatched java type: " + javaType);
            return null;
        }
    }
}
