/*
Copyright (C) 2013, 2016 Bengt Martensson.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; either version 3 of the License, or (at
your option) any later version.

This program is distributed in the hope that it will be useful, but
WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
General Public License for more details.

You should have received a copy of the GNU General Public License along with
this program. If not, see http://www.gnu.org/licenses/.
 */
package org.harctoolbox.jirc;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import org.harctoolbox.girr.RemoteSet;

/**
 * This class parses the <a href="http://lirc.org/html/lircd.conf.html">Lircd configuration file(s)</a>.
 * Its preferred public members are the static functions parseConfig,
 * returning a {@link org.harctoolbox.girr.RemoteSet RemoteSet}.
 */

public final class ConfigFile {

    private static class EofException extends Exception {

        EofException(String str) {
            super(str);
        }

        private EofException() {
            super();
        }
    }

    /**
     * Default character set input files.
     */
    public final static String defaultCharsetName = "WINDOWS-1252";

    private List<IrRemote> remotes;
    private LineNumberReader reader;
    private String line;
    private String[] words;

    private ConfigFile(File configFileName, String source, String charsetName, boolean acceptLircCode) throws UnsupportedEncodingException, FileNotFoundException, IOException {
        this(new InputStreamReader(new FileInputStream(configFileName), charsetName), source, acceptLircCode);
    }

    private ConfigFile(Reader reader, String source, boolean accepLircCode) throws IOException {
        this.remotes = new ArrayList<>();
        this.reader = new LineNumberReader(reader);
        line = null;
        words = new String[0];

        remotes = remotes(source, accepLircCode);
        IrRemote last = null;
        for (IrRemote rem : remotes) {
            //rem.setSource(source);
            rem.next = null;
            if (last != null)
                last.next = rem;
            last = rem;
        }
    }

    /**
     * Reads the file given as first argument and deliveres a Collection of {@link org.harctoolbox.jirc.IrRemote IrRemote}'s.
     *
     * @param filename lirc.conf file
     * @param charsetName Name of the Charset used for reading.
     * @param acceptLircCode if true, so-called LircCode remotes (without timing information, depending on special drivers),
     * will be accepted.
     * @return Collection of IrRemote's.
     * @throws IOException Misc IO problem
     */
    public static Collection<IrRemote> readConfig(File filename, String charsetName, boolean acceptLircCode) throws IOException {
        //System.err.println("Parsing " + filename.getCanonicalPath());
        if (filename.isFile()) {
            ConfigFile config = new ConfigFile(filename, filename.getCanonicalPath(), charsetName, acceptLircCode);
            return config.remotes;
        } else if (filename.isDirectory()) {
            File[] files = filename.listFiles();
            HashMap<String, IrRemote> dictionary = new HashMap<>();
            for (File file : files) {
                // The program handles nonsensical files fine, however rejecting some
                // obviously irrelevant files saves time and log entries.
                if (file.getName().endsWith(".jpg") || file.getName().endsWith(".png")
                        || file.getName().endsWith(".gif") || file.getName().endsWith(".html")) {
                    System.err.println("Rejecting file " + file.getCanonicalPath());
                    continue;
                }
                Collection<IrRemote> map = readConfig(file, charsetName, acceptLircCode);

                for (IrRemote irRemote : map) {
                    String remoteName = irRemote.getName();
                    int n = 1;
                    while (dictionary.containsKey(remoteName))
                        remoteName = irRemote.getName() + "$" + n++;

                    if (n > 1)
                        System.err.println("Warning: remote name " + irRemote.getName()
                                + " (source: " + irRemote.getSource()
                                + ") already present, renaming to " + remoteName);
                    dictionary.put(remoteName, irRemote);
                }
            }
            return dictionary.values();
        } else if (!filename.canRead())
            throw new FileNotFoundException(filename.getCanonicalPath());
        else
            return null;
    }

    public static Collection<IrRemote> readConfig(Reader reader, String source, boolean acceptLircCode) throws IOException {
        ConfigFile config = new ConfigFile(reader, source, acceptLircCode);
        return config.remotes;
    }

    /**
     * Parses a Lirc configuration file, and returns a {@link org.harctoolbox.girr.RemoteSet RemoteSet}.
     *
     * @param filename Lirc configuration file
     * @param charsetName Name of the {@link java.nio.charset.Charset character set} for reading, e.g. URF-8, ISO-8859-1, WINDOWS-1252.
     * @param acceptLircCode If true, so-called Lirccode files are processed (but will be of limited use anyhow).
     * @param generateParameters If true, parameters are generated.
     * @param generateCcf If true, the ccf/hex form of the raw signals is generated.
     * @param creatingUser Name of the creating user; for documentation purposes.
     * @param alternatingSigns If true, the generated signals will precede gaps with a minus sign.
     * @return RemoteSet as per <a href="http://www.harctoolbox.org/Girr.html">Girr specification</a>.
     * @throws IOException Misc IO errors.
     */
    public static RemoteSet parseConfig(File filename, String charsetName, boolean acceptLircCode,
            boolean generateParameters, boolean generateCcf, String creatingUser, boolean alternatingSigns) throws IOException {
        Collection<IrRemote> lircRemotes = readConfig(filename, charsetName, acceptLircCode);
        return IrRemote.newRemoteSet(lircRemotes, filename.getCanonicalPath(), generateParameters, generateCcf,
                creatingUser, alternatingSigns, 0 /* debug */);
    }

    /**
     * Parses a {@link java.io.Reader Reader} for one or many Lirc configuration "file(s)",
     * and returns a {@link org.harctoolbox.girr.RemoteSet RemoteSet}.
     *
     * @param reader Reader delivering a Lirc configuration file.
     * @param source String containing the source of the informatsion, for documentation purposes.
     * @param acceptLircCode If true, so-called Lirccode files are rejected, otherwise they are processed (but will be of limited use anyhow)
     * @param generateParameters If true, parameters are generated.
     * @param generateCcf If true, the ccf/hex form of the raw signals is generated.
     * @param creatingUser Name of the creating user; for documentation purposes.
     * @param alternatingSigns If true, the generated signals will precede gaps with a minus sign.
     * @return RemoteSet as per <a href="http://www.harctoolbox.org/Girr.html">Girr specification</a>.
     * @throws IOException Misc IO errors.
     */
    public static RemoteSet parseConfig(Reader reader, String source, boolean acceptLircCode,
            boolean generateParameters, boolean generateCcf, String creatingUser, boolean alternatingSigns) throws IOException {
        Collection<IrRemote> lircRemotes = readConfig(reader, source, acceptLircCode);
        return IrRemote.newRemoteSet(lircRemotes, source, generateParameters, generateCcf,
                creatingUser, alternatingSigns, 0 /* debug */);
    }

    private static class ProtocolParameters {
        private String name = null;
        private String driver;

        List<String> flags = new ArrayList<>();
        HashMap<String, Long> unaryParameters = new HashMap<>();
        HashMap<String, IrRemote.XY> binaryParameters = new HashMap<>();

        public void add(String name, long x) {
            unaryParameters.put(name, x);
        }

        public void add(String name, long x, long y) {
            binaryParameters.put(name, new IrRemote.XY(x, y));
        }
    }

    private List<IrRemote> remotes(String source, boolean acceptLircCode) throws IOException {
        List<IrRemote> rems = new ArrayList<>();
        while (true) {
            try {
                IrRemote remote = remote();
                remote.setSource(source);
                if (remote.isTimingInfo() || acceptLircCode)
                    rems.add(remote);
                else
                    System.err.println("Ignoring timingless remote " + remote.getName() + " in " + remote.getSource());
            } catch (ParseException ex) {
                try {
                    lookFor("end", "remote");
                } catch (EofException ex1) {
                    return rems;
                }
            } catch (EofException ex) {
                return rems;
            }
        }
    }

    private IrRemote remote() throws IOException, ParseException, EofException {
        lookFor("begin", "remote");
        ProtocolParameters parameters = parameters();
        List<IrNCode> codes = codes();
        gobble("end", "remote");

        IrRemote irRemote = new IrRemote(parameters.name, parameters.driver, parameters.flags,
                parameters.unaryParameters, parameters.binaryParameters, codes);
        return irRemote;
    }

    private void readLine() throws IOException, EofException {
        if (line != null)
            return;
        words = new String[0];
        while (words.length == 0) {
            line = reader.readLine();
            if (line == null)
                throw new EofException();

            int idx = line.indexOf('#');
            if (idx != -1)
                line = line.substring(0, idx).trim();
            if (!line.isEmpty())
                words = line.trim().split("\\s+");
        }
    }

    private void consumeLine() {
        line = null;
    }

    private void gobble(String... tokens) throws IOException, EofException, ParseException {
        while (true) {
            readLine();
            for (int i = 0; i < tokens.length; i++)
                if (words.length < tokens.length || !words[i].equalsIgnoreCase(tokens[i]))
                    throw new ParseException("Did not find " + join(tokens), reader.getLineNumber());
            consumeLine();
            break;
        }
    }

    private void lookFor(String... tokens) throws IOException, EofException {
        while (true) {
            readLine();
            boolean hit = true;
            for (int i = 0; i < tokens.length; i++) {
                if (words.length < tokens.length || !words[i].equalsIgnoreCase(tokens[i])) {
                    hit = false;
                    break;
                }
            }
            consumeLine();
            if (hit)
                return;
        }
    }

    private static String join(String[] str) {
        return join(str, 0);
    }

    private static String join(String[] str, int start) {
        StringBuilder result = new StringBuilder();
        for (int i = start; i < str.length; i++)
            result.append(str[i]).append(" ");
        result.setLength(result.length() - 1);
        return result.toString();
    }

    private ProtocolParameters parameters() throws IOException, ParseException, EofException {
        ProtocolParameters parameters = new ProtocolParameters();
        while (true) {
            readLine();
            switch (words[0]) {
                case "name":
                    parameters.name = words[1];
                    break;
                case "driver":
                    parameters.driver = words[1];
                    break;
                case "flags":
                    parameters.flags = flags(words);
                    break;
                case "begin":
                    return parameters;
                default:
                    try {
                    switch (words.length) {
                        case 2:
                            parameters.add(words[0], IrNCode.parseLircNumber(words[1]));
                            break;
                        case 3:
                            parameters.add(words[0], IrNCode.parseLircNumber(words[1]), IrNCode.parseLircNumber(words[2]));
                            break;
                        default:
                            throw new ParseException("silly parameter decl: " + line, reader.getLineNumber());
                    }
                    } catch (NumberFormatException ex) {
                        // except for a warning, just ignore unparsable parameters
                        System.err.println("Could not parse line \"" + line + "\": " + ex);
                    }
            }
            consumeLine();
        }
    }

    private List<String> flags(String[] words) {
        String str = join(words, 1);
        String array[] = str.split("\\s*\\|\\s*");
        return Arrays.asList(array);
    }

    private List<IrNCode> codes() throws IOException, EofException, ParseException {
        try {
            return cookedCodes();
        } catch (ParseException ex) {
            return rawCodes();
        }
    }

    private List<IrNCode> cookedCodes() throws IOException, EofException, ParseException {
        gobble("begin", "codes");
        List<IrNCode> codes = new ArrayList<>();
        while (true) {
            try {
                IrNCode code = cookedCode();
                codes.add(code);
            } catch (ParseException ex) {
                break;
            }
        }
        gobble("end", "codes");
        return codes;
    }

    private IrNCode cookedCode() throws IOException, EofException, ParseException {
        readLine();
        if (words.length < 2)
            throw new ParseException("", reader.getLineNumber());
        if (words[0].equalsIgnoreCase("end") && words[1].equalsIgnoreCase("codes"))
            throw new ParseException("", reader.getLineNumber());
        List<Long> codes = new ArrayList<>();
        for (int i = 1; i < words.length; i++)
            codes.add(IrNCode.parseLircNumber(words[i]));
        IrNCode irNCode = new IrNCode(words[0], codes);

        consumeLine();
        return irNCode;
    }

    private List<IrNCode> rawCodes() throws IOException, EofException, ParseException {
        gobble("begin", "raw_codes");
        List<IrNCode> codes = new ArrayList<>();
        while (true) {
            try {
                IrNCode code = rawCode();
                codes.add(code);
            } catch (ParseException ex) {
                break;
            }
        }
        gobble("end", "raw_codes");
        return codes;
    }

    private IrNCode rawCode() throws IOException, EofException, ParseException {
        readLine();
        if (words.length < 2)
            throw new ParseException("", reader.getLineNumber());
        if (words[0].equalsIgnoreCase("end") && words[1].equalsIgnoreCase("raw_codes"))
            throw new ParseException("", reader.getLineNumber());
        if (!words[0].equalsIgnoreCase("name"))
            throw new ParseException("", reader.getLineNumber());
        String cmdName = words[1];
        consumeLine();
        List<Integer> codes = integerList();
        IrNCode irNCode = new IrNCode(cmdName, 0, codes);
        return irNCode;
    }

    private List<Integer> integerList() throws IOException, EofException {
        List<Integer> numbers = new ArrayList<>();
        while (true) {
            readLine();
            try {
                for (String w : words)
                    numbers.add(Integer.parseInt(w));
            } catch (NumberFormatException ex) {
                return numbers;
            }
            consumeLine();
        }
    }
}
