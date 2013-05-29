package com.ning.killbill.generators.ruby;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.List;

import com.ning.killbill.generators.GeneratorException;
import com.ning.killbill.objects.Annotation;
import com.ning.killbill.objects.ClassEnumOrInterface;
import com.ning.killbill.objects.Constructor;
import com.ning.killbill.objects.Field;

public class RubyGenerator extends BaseGenerator {

    private final static int INDENT_LEVEL = 2;

    private int curIndent = 0;

    public RubyGenerator() {
        this.curIndent = 0;
    }

    @Override
    protected void generateClass(final ClassEnumOrInterface obj, final File outputDir) throws GeneratorException {
        final File output = new File(outputDir, camelToUnderscore(obj.getName() + ".rb"));


        Writer w = null;
        try {
            w = new PrintWriter(output);
            writeWithIndetation("module KillBillClient", w, 0);
            writeWithIndetation("module Model", w, INDENT_LEVEL);
            writeWithIndetation("module " + obj.getName() + " < Resource", w, INDENT_LEVEL);
            final Constructor ctor = getJsonCreatorCTOR(obj);
            boolean first = true;
            for (Field f : ctor.getOrderedArguments()) {
                final String attribute = camelToUnderscore(getJsonPropertyAnnotationValue(obj, f));
                if (first) {
                    first = false;
                    writeWithIndetation("attribute: " + attribute, w, INDENT_LEVEL);
                } else {
                    writeWithIndetation("attribute: " + attribute, w, 0);
                }
            }
            writeWithIndetation("end", w, -INDENT_LEVEL);
            writeWithIndetation("end", w, -INDENT_LEVEL);
            writeWithIndetation("end", w, -INDENT_LEVEL);

            w.flush();
            w.close();

        } catch (FileNotFoundException e) {
            throw new GeneratorException("Failed to generate file " + obj.getName(), e);
        } catch (IOException e) {
            throw new GeneratorException("Failed to generate file " + obj.getName(), e);
        }
    }

    private String getJsonPropertyAnnotationValue(final ClassEnumOrInterface obj, final Field f)  throws GeneratorException {
        for (Annotation a : f.getAnnotations()) {
            if ("JsonProperty".equals(a.getName())) {
                return a.getValue();
            }
        }
        throw new GeneratorException("Could not find a JsonProperty annotation for object " + obj.getName() + " and field " + f.getName());
    }

    private Constructor getJsonCreatorCTOR(final ClassEnumOrInterface obj) throws GeneratorException {
        final List<Constructor> ctors = obj.getCtors();
        for (Constructor cur : ctors) {
            if (cur.getAnnotations() == null || cur.getAnnotations().size() == 0) {
                continue;
            }
            for (final Annotation a : cur.getAnnotations()) {
                if ("JsonCreator".equals(a.getName())) {
                    return cur;
                }
            }
        }
        throw new GeneratorException("Could not find a CTOR for " + obj.getName() + " with a JsonCreator annotation");
    }

    private void writeWithIndetation(final String str, final Writer w, int curIndentOffest) throws IOException {
        curIndent += curIndentOffest;
        for (int i = 0; i < curIndent; i++) {
            w.write(" ");
        }
        w.write(str);
        w.write("\n");
    }
}
