killbill-java-parser
====================

[![build status](https://secure.travis-ci.org/killbill/killbill-java-parser.png)](https://travis-ci.org/killbill/killbill-java-parser)

This is a tool built to help with Kill Bill client APIs and Jruby conversion layer for ruby plugins.
The tool takes as an input some java src directory/files and translate those into the desired format.

It currently supports the following:
* JRUBY_PLUGIN_API : Transformation for model and API classes required in the killbill-plugin-framework-ruby repo.
* RUBY_CLIENT_API : Transformation for ruby models required for the ruby client library

The tool is made out of:
* A parser that understands java grammar and allow to parse Kill Bill java files
* A specific generator for the desired output.

Usage
=====

```
java -cp target/killbill-java-parser-0.0.1-SNAPSHOT.jar com.ning.killbill.KillbillParser --help
  Options:
  *  -x, --classGeneratorExcludeFilter
       A optional filter list of java packages for the parser
       Default: []
  *  -d, --debug
       Turn on debug traces
       Default: false
  * -i, --input
       The input file/jar/directory for the java sources to prase
       Default: []
  *  -m, --mode
       The generator mode
       Default: NON_APPLICABLE
  * -o, --output
       The output directory for the objects created
  *  -q, --packageGeneratorIncludeFilter
       A optional filter list of java packages for the parser
       Default: []
  *  -p, --packageParserIncludeFilter
       A optional filter list of java packages for the parser
       Default: []
  * -t, --target
       The target generator
    -d, --debug
```

Example
=====

To generate types for the PHP client in an output directory:
```
$ java -cp target/killbill-java-parser-0.0.1-SNAPSHOT.jar com.ning.killbill.KillbillParser -i file://[PATH_TO_KILLBILL_REPO]/jaxrs/src/main/java/org -o ./output -t PHP_CLIENT_API -p org.killbill.billing.jaxrs.json
```
