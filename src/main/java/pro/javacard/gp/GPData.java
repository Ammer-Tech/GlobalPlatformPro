/*
 * GlobalPlatformPro - GlobalPlatform tool
 *
 * Copyright (C) 2015-2017 Martin Paljak, martin@martinpaljak.net
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3.0 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA
 */
package pro.javacard.gp;

import apdu4j.HexUtils;
import apdu4j.ISO7816;
import com.payneteasy.tlv.*;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pro.javacard.gp.GPKey.Type;

import javax.smartcardio.CardChannel;
import javax.smartcardio.CardException;
import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;
import java.io.IOException;
import java.io.PrintStream;
import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

import static pro.javacard.gp.GlobalPlatform.CLA_GP;

// Various constants from GP specification and other sources
// Methods to pretty-print those structures and constants.
public final class GPData {
    private static final Logger logger = LoggerFactory.getLogger(GPData.class);

    // SD states
    public static final byte readyStatus = 0x1;
    public static final byte initializedStatus = 0x7;
    public static final byte securedStatus = 0xF;
    public static final byte lockedStatus = 0x7F;
    public static final byte terminatedStatus = (byte) 0xFF;
    // See GP 2.1.1 Table 9-7: Application Privileges
    @Deprecated
    public static final byte defaultSelectedPriv = 0x04;
    @Deprecated
    public static final byte cardLockPriv = 0x10;
    @Deprecated
    public static final byte cardTerminatePriv = 0x08;
    @Deprecated
    public static final byte securityDomainPriv = (byte) 0x80;
    // Default test key TODO: provide getters for arrays, this class should be kept public
    static final byte[] defaultKeyBytes = HexUtils.hex2bin("404142434445464748494A4B4C4D4E4F");
    // Default ISD AID-s
    static final byte[] defaultISDBytes = HexUtils.hex2bin("A000000151000000");
    static final Map<Integer, String> sw = new HashMap<>();

    static {
        // Some generics.
        sw.put(0x6400, "No specific diagnosis"); // Table 11-10
        sw.put(0x6700, "Wrong length (Lc)"); // Table 11-10
        sw.put(0x6D00, "Invalid INStruction"); // Table 11-10
        sw.put(0x6E00, "Invalid CLAss"); // Table 11-10

        sw.put(0x6283, "Card Life Cycle State is CARD_LOCKED"); // Table 11-83: SELECT Warning Condition

        sw.put(0x6581, "Memory failure"); // 2.3 Table 11-26: DELETE Error Conditions

        sw.put(0x6882, "Secure messaging not supported");  // 2.3 Table 11-63

        sw.put(0x6982, "Security status not satisfied");  // 2.3 Table 11-78
        sw.put(0x6985, "Conditions of use not satisfied");  // 2.3 Table 11-78

        sw.put(0x6A80, "Wrong data/incorrect values in data"); // Table 11-78
        sw.put(0x6A81, "Function not supported e.g. card Life Cycle State is CARD_LOCKED"); // 2.3 Table 11-63
        sw.put(0x6A82, "Application/file not found"); // 2.3 Table 11-26: DELETE Error Conditions
        sw.put(0x6A84, "Not enough memory space"); // 2.3 Table 11-15
        sw.put(0x6A86, "Incorrect P1/P2"); // 2.3 Table 11-15
        sw.put(0x6A88, "Referenced data not found");  // 2.3 Table 11-78
    }

    @Deprecated
    public static IBerTlvLogger getLoggerInstance() {
        return new IBerTlvLogger() {
            @Override
            public boolean isDebugEnabled() {
                return true;
            }

            @Override
            public void debug(String s, Object... objects) {
                logger.trace(s, objects);
            }
        };
    }

    // GP 2.1.1 9.1.6
    // GP 2.2.1 11.1.8
    public static String get_key_type_coding_string(int type) {
        if ((0x00 <= type) && (type <= 0x7f))
            return "Reserved for private use";
        // symmetric
        if (0x80 == type)
            return "DES - mode (ECB/CBC) implicitly known";
        if (0x81 == type)
            return "Reserved (Triple DES)";
        if (0x82 == type)
            return "Triple DES in CBC mode";
        if (0x83 == type)
            return "DES in ECB mode";
        if (0x84 == type)
            return "DES in CBC mode";
        if (0x85 == type)
            return "Pre-Shared Key for Transport Layer Security";
        if (0x88 == type)
            return "AES (16, 24, or 32 long keys)";
        if (0x90 == type)
            return "HMAC-SHA1 - length of HMAC is implicitly known";
        if (0x91 == type)
            return "MAC-SHA1-160 - length of HMAC is 160 bits";
        if (type == 0x86 || type == 0x87 || ((0x89 <= type) && (type <= 0x8F)) || ((0x92 <= type) && (type <= 0x9F)))
            return "RFU (asymmetric algorithms)";
        // asymmetric
        if (0xA0 == type)
            return "RSA Public Key - public exponent e component (clear text)";
        if (0xA1 == type)
            return "RSA Public Key - modulus N component (clear text)";
        if (0xA2 == type)
            return "RSA Private Key - modulus N component";
        if (0xA3 == type)
            return "RSA Private Key - private exponent d component";
        if (0xA4 == type)
            return "RSA Private Key - Chinese Remainder P component";
        if (0xA5 == type)
            return "RSA Private Key - Chinese Remainder Q component";
        if (0xA6 == type)
            return "RSA Private Key - Chinese Remainder PQ component";
        if (0xA7 == type)
            return "RSA Private Key - Chinese Remainder DP1 component";
        if (0xA8 == type)
            return "RSA Private Key - Chinese Remainder DQ1 component";
        if ((0xA9 <= type) && (type <= 0xFE))
            return "RFU (asymmetric algorithms)";
        if (0xFF == type)
            return "Extended Format";

        return "UNKNOWN";
    }

    public static GPKey getDefaultKey() {
        return new GPKey(defaultKeyBytes, Type.DES3);
    }

    // Print the key template
    public static void pretty_print_key_template(List<GPKey> list, PrintStream out) {
        boolean factory_keys = false;
        out.flush();
        for (GPKey k : list) {
            // Descriptive text about the key
            final String nice;
            if (k.getType() == Type.RSAPUB) {
                nice = "(RSA-" + k.getLength() * 8 + " public)";
            } else if (k.getType() == Type.AES) {
                nice = "(AES-" + k.getLength() * 8 + ")";
            } else {
                nice = "";
            }

            // Detect unaddressable factory keys
            if (k.getVersion() == 0x00 || k.getVersion() == 0xFF)
                factory_keys = true;

            // print
            out.println(String.format("Version: %3d (0x%02X) ID: %3d (0x%02X) type: %-4s length: %3d %s", k.getVersion(), k.getVersion(), k.getID(), k.getID(), k.getType(), k.getLength(), nice));
        }
        if (factory_keys) {
            out.println("Key version suggests factory keys");
        }
        out.flush();
    }

    // GP 2.1.1 9.3.3.1
    // GP 2.2.1 11.1.8
    // TODO: move to GPKey
    public static List<GPKey> get_key_template_list(byte[] data) throws GPException {
        List<GPKey> r = new ArrayList<>();

        BerTlvParser parser = new BerTlvParser();
        BerTlvs tlvs = parser.parse(data);
        BerTlvLogger.log("    ", tlvs, GPData.getLoggerInstance());

        BerTlv keys = tlvs.find(new BerTag(0xE0));
        if (keys != null && keys.isConstructed()) {
            for (BerTlv key : keys.findAll(new BerTag(0xC0))) {
                byte[] tmpl = key.getBytesValue();
                if (tmpl.length == 0) {
                    // Fresh SSD with an empty template.
                    logger.info("Key template has zero length (empty). Skipping.");
                    continue;
                }
                if (tmpl.length < 4) {
                    throw new GPDataException("Key info template shorter than 4 bytes", tmpl);
                }
                int id = tmpl[0] & 0xFF;
                int version = tmpl[1] & 0xFF;
                int type = tmpl[2] & 0xFF;
                int length = tmpl[3] & 0xFF;
                if (type == 0xFF) {
                    // TODO
                    throw new GPDataException("Extended key template not yet supported", tmpl);
                }
                // XXX: RSAPUB keys have two components A1 and A0, gets called with A1 and A0 (exponent) discarded
                r.add(new GPKey(version, id, length, type));
            }
        }
        return r;
    }

    // GP 2.1.1: F.2 Table F-1
    // Tag 66 with nested 73
    public static void pretty_print_card_data(byte[] data) {
        BerTlvParser parser = new BerTlvParser();
        BerTlvs tlvs = parser.parse(data);
        BerTlvLogger.log("    ", tlvs, GPData.getLoggerInstance());

        BerTlv cd = tlvs.find(new BerTag(0x66));
        if (cd != null && cd.isConstructed()) {
            BerTlv isdd = tlvs.find(new BerTag(0x73));
            if (isdd != null) {
                // Loop all sub-values
                for (BerTlv vt : isdd.getValues()) {
                    BerTlv ot = vt.find(new BerTag(0x06));
                    if (ot != null) {
                        String oid = oid2string(ot.getBytesValue());
                        System.out.println("Tag " + new BigInteger(1, vt.getTag().bytes).toString(16) + ": " + oid);

                        if (oid.equals("1.2.840.114283.1")) {
                            System.out.println("-> Global Platform card");
                        }
                        if (oid.startsWith("1.2.840.114283.2")) {
                            String[] p = oid.substring("1.2.840.114283.2.".length()).split("\\.");
                            System.out.println("-> GP Version: " + String.join(".", p));
                        }

                        if (oid.startsWith("1.2.840.114283.4")) {
                            String[] p = oid.substring("1.2.840.114283.4.".length()).split("\\.");
                            if (p.length == 2) {
                                System.out.println("-> GP SCP0" + p[0] + " i=" + String.format("%02x", Integer.valueOf(p[1])));
                            } else {
                                if (oid.equals("1.2.840.114283.4.0")) {
                                    System.out.println("-> GP SCP80 i=00");
                                }
                            }
                        }
                        if (oid.startsWith("1.3.6.1.4.1.42.2.110.1")) {
                            String p = oid.substring("1.3.6.1.4.1.42.2.110.1.".length());
                            if (p.length() == 1) {
                                System.out.println("-> JavaCard v" + p);
                            }
                        }
                    }
                }
            }
        } else {
            System.out.println("No Card Data");
        }
    }

    public static void pretty_print_card_capabilities(byte[] data) throws GPDataException {
        // BUGFIX: exist cards that return nested 0x67 tag with GET DATA with GP CLA
        if (data[0] == 0x67 && data[2] == 0x67)
            data = Arrays.copyOfRange(data, 2, data.length);
        // END BUGFIX

        BerTlvParser parser = new BerTlvParser();
        BerTlvs tlvs = parser.parse(data);
        BerTlvLogger.log("    ", tlvs, GPData.getLoggerInstance());
        if (tlvs != null) {
            BerTlv caps = tlvs.find(new BerTag(0x67));
            if (caps != null) {
                for (BerTlv v : caps.getValues()) {
                    BerTlv t = v.find(new BerTag(0xA0));
                    if (t != null) {
                        int scp = t.find(new BerTag(0x80)).getIntValue();
                        byte[] is = t.find(new BerTag(0x81)).getBytesValue();
                        System.out.format("Supports: SCP%02X", scp);
                        for (int i = 0; i < is.length; i++) {
                            System.out.format(" i=%02X", is[i]);
                        }
                        BerTlv keylens = t.find(new BerTag(0x82));
                        if (keylens != null) {
                            System.out.print(" with");
                            if ((keylens.getIntValue() & 0x01) == 0x01) {
                                System.out.print(" AES-128");
                            }
                            if ((keylens.getIntValue() & 0x02) == 0x02) {
                                System.out.print(" AES-196");
                            }
                            if ((keylens.getIntValue() & 0x04) == 0x04) {
                                System.out.print(" AES-256");
                            }
                        }
                        System.out.println();
                        continue;
                    }
                    t = v.find(new BerTag(0x81));
                    if (t != null) {
                        System.out.println("Available DOM privileges: " + GPRegistryEntry.Privileges.fromBytes(t.getBytesValue()));
                        continue;
                    }
                    t = v.find(new BerTag(0x82));
                    if (t != null) {
                        System.out.println("Available APP privileges: " + GPRegistryEntry.Privileges.fromBytes(t.getBytesValue()));
                        continue;
                    }
                }
            }
        }
    }


    // NB! This assumes a selected (I)SD!
    public static void dump(CardChannel channel) throws CardException, GPException {
        byte[] cplc = fetchCPLC(channel);
        if (cplc != null) {
            System.out.println(GPData.CPLC.fromBytes(cplc).toPrettyString());
        }

        // IIN
        byte[] iin = getData(channel, 0x00, 0x42, "IIN", false);
        if (iin != null) {
            System.out.println("IIN: " + HexUtils.bin2hex(iin));
        }
        // CIN
        byte[] cin = getData(channel, 0x00, 0x45, "CIN", false);
        if (cin != null) {
            System.out.println("CIN: " + HexUtils.bin2hex(iin));
        }
        // FIXME: SSC?
//        BerTlvParser parser = new BerTlvParser();
//        BerTlvs tlvs = parser.parse(resp.getData());
//        BerTlvLogger.log("    ", tlvs, GPData.getLoggerInstance());
//        if (tlvs != null) {
//            BerTlv ssc = tlvs.find(new BerTag(0xC1));
//            if (ssc != null) {
//                out.println(HexUtils.bin2hex(ssc.getBytesValue()));
//            }
//        }
        // Print Card Data
        System.out.println("Card Data: ");
        byte[] cardData = getData(channel, 0x00, 0x66, "Card Data", false);
        if (cardData != null) {
            pretty_print_card_data(cardData);
        }
        // Print Card Capabilities
        System.out.println("Card Capabilities: ");
        byte[] cardCapabilities = getData(channel, 0x00, 0x67, "Card Capabilities", false);
        if (cardCapabilities != null) {
            pretty_print_card_capabilities(cardCapabilities);
        }

        // Print Key Info Template
        byte[] keyInfo = fetchKeyInfoTemplate(channel);
        if (keyInfo != null) {
            pretty_print_key_template(GPData.get_key_template_list(keyInfo), System.out);
        }
    }


    // Just to encapsulate tag constants behind meaningful name
    public static byte[] fetchCPLC(CardChannel channel) throws CardException {
        return getData(channel, 0x9f, 0x7f, "CPLC", true);
    }

    public static byte[] fetchKeyInfoTemplate(CardChannel channel) throws CardException {
        return getData(channel, 0x00, 0xE0, "Key Info Template", false);
    }


    public static String sw2str(int sw) {
        String msg = GPData.sw.get(sw);
        if (msg == null)
            return String.format("0x%04X", sw);
        return String.format("0x%04X (%s)", sw, msg);
    }

    public static String oid2string(byte[] oid) {
        try {
            // Prepend 0x06 tag, if not present
            // XXX: if ber-tlv allows to fetch constructed data, this is not needed
            if (oid[0] != 0x06) {
                oid = GPUtils.concatenate(new byte[]{0x06, (byte) oid.length}, oid);
            }
            ASN1ObjectIdentifier realoid = (ASN1ObjectIdentifier) ASN1ObjectIdentifier.fromByteArray(oid);
            if (realoid == null)
                throw new IllegalArgumentException("Could not parse OID from " + HexUtils.bin2hex(oid));
            return realoid.toString();
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not handle " + HexUtils.bin2hex(oid));
        }
    }

    public static GlobalPlatform.GPSpec oid2version(byte[] bytes) throws GPDataException {
        String oid = oid2string(bytes);
        if (oid.equals("1.2.840.114283.2.2.1.1")) {
            return GlobalPlatform.GPSpec.GP211;
        } else if (oid.equals("1.2.840.114283.2.2.2")) {
            return GlobalPlatform.GPSpec.GP22;
        } else if (oid.equals("1.2.840.114283.2.2.2.1")) {
            return GlobalPlatform.GPSpec.GP22; // No need to make a difference
        } else {
            throw new GPDataException("Unknown GP version OID: " + oid, bytes);
        }
    }

    public static byte[] getData(CardChannel channel, int p1, int p2, String name, boolean failsafe) throws CardException {
        logger.trace("GET DATA({})", name);
        ResponseAPDU resp = channel.transmit(new CommandAPDU(CLA_GP, ISO7816.INS_GET_DATA, p1, p2, 256));
        if (failsafe && resp.getSW() != ISO7816.SW_NO_ERROR)
            resp = channel.transmit(new CommandAPDU(0x00, ISO7816.INS_GET_DATA, p1, p2, 256));
        if (resp.getSW() == ISO7816.SW_NO_ERROR) {
            return resp.getData();
        } else if (resp.getSW() == 0x6A88) {
            logger.debug("GET DATA({}): N/A", name);
            return null;
        } else {
            logger.warn("GET DATA({}) not supported", name);
            return null;
        }
    }

    public static final class CPLC {

        private HashMap<Field, byte[]> values = new HashMap<>();

        private CPLC(byte[] data) {
            short offset = 0;
            values.put(Field.ICFabricator, Arrays.copyOfRange(data, offset, offset + 2));
            offset += 2;
            values.put(Field.ICType, Arrays.copyOfRange(data, offset, offset + 2));
            offset += 2;
            values.put(Field.OperatingSystemID, Arrays.copyOfRange(data, offset, offset + 2));
            offset += 2;
            values.put(Field.OperatingSystemReleaseDate, Arrays.copyOfRange(data, offset, offset + 2));
            offset += 2;
            values.put(Field.OperatingSystemReleaseLevel, Arrays.copyOfRange(data, offset, offset + 2));
            offset += 2;
            values.put(Field.ICFabricationDate, Arrays.copyOfRange(data, offset, offset + 2));
            offset += 2;
            values.put(Field.ICSerialNumber, Arrays.copyOfRange(data, offset, offset + 4));
            offset += 4;
            values.put(Field.ICBatchIdentifier, Arrays.copyOfRange(data, offset, offset + 2));
            offset += 2;
            values.put(Field.ICModuleFabricator, Arrays.copyOfRange(data, offset, offset + 2));
            offset += 2;
            values.put(Field.ICModulePackagingDate, Arrays.copyOfRange(data, offset, offset + 2));
            offset += 2;
            values.put(Field.ICCManufacturer, Arrays.copyOfRange(data, offset, offset + 2));
            offset += 2;
            values.put(Field.ICEmbeddingDate, Arrays.copyOfRange(data, offset, offset + 2));
            offset += 2;
            values.put(Field.ICPrePersonalizer, Arrays.copyOfRange(data, offset, offset + 2));
            offset += 2;
            values.put(Field.ICPrePersonalizationEquipmentDate, Arrays.copyOfRange(data, offset, offset + 2));
            offset += 2;
            values.put(Field.ICPrePersonalizationEquipmentID, Arrays.copyOfRange(data, offset, offset + 4));
            offset += 4;
            values.put(Field.ICPersonalizer, Arrays.copyOfRange(data, offset, offset + 2));
            offset += 2;
            values.put(Field.ICPersonalizationDate, Arrays.copyOfRange(data, offset, offset + 2));
            offset += 2;
            values.put(Field.ICPersonalizationEquipmentID, Arrays.copyOfRange(data, offset, offset + 4));
            offset += 4;
        }

        public static CPLC fromBytes(byte[] data) throws GPDataException {
            if (data == null)
                throw new IllegalArgumentException("data is null");
            if (data.length < 0x2A)
                throw new GPDataException(String.format("Input can't be valid CPLC if length is only %02X!", data.length));
            // Remove tag, if present
            if (data[0] == (byte) 0x9f && data[1] == (byte) 0x7f && data[2] == (byte) 0x2A)
                data = Arrays.copyOfRange(data, 3, data.length);
            return new CPLC(data);
        }

        public byte[] get(Field f) {
            return values.get(f);
        }

        public String toString() {
            return Arrays.asList(Field.values()).stream().map(i -> i.toString() + "=" + HexUtils.bin2hex(values.get(i))).collect(Collectors.joining(", ", "[CPLC: ", "]"));
        }

        public String toPrettyString() {
            return Arrays.asList(Field.values()).stream().map(i -> i.toString() + "=" + HexUtils.bin2hex(values.get(i)) + (i.toString().endsWith("Date") ? " (" + toDateFailsafe(values.get(i)) + ")" : "")).collect(Collectors.joining("\n      ", "CPLC: ", "\n"));
        }

        public enum Field {
            ICFabricator,
            ICType,
            OperatingSystemID,
            OperatingSystemReleaseDate,
            OperatingSystemReleaseLevel,
            ICFabricationDate,
            ICSerialNumber,
            ICBatchIdentifier,
            ICModuleFabricator,
            ICModulePackagingDate,
            ICCManufacturer,
            ICEmbeddingDate,
            ICPrePersonalizer,
            ICPrePersonalizationEquipmentDate,
            ICPrePersonalizationEquipmentID,
            ICPersonalizer,
            ICPersonalizationDate,
            ICPersonalizationEquipmentID
        }

        public static String toDate(byte[] v) throws GPDataException {
            String sv = HexUtils.bin2hex(v);
            try {
                int y = Integer.parseInt(sv.substring(0, 1));
                int d = Integer.parseInt(sv.substring(1, 4));
                if (d > 366) {
                    throw new GPDataException("Invalid CPLC date format: " + sv);
                }
                // Make 0000 show something meaningful
                if (d == 0) {
                    d = 1;
                }
                GregorianCalendar gc = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
                // FIXME: 2010 is hardcoded.
                gc.set(GregorianCalendar.YEAR, 2010 + y);
                gc.set(GregorianCalendar.DAY_OF_YEAR, d);
                SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd");
                return f.format(gc.getTime());
            } catch (NumberFormatException e) {
                throw new GPDataException("Invalid CPLC date: " + sv, e);
            }
        }

        public static String toDateFailsafe(byte[] v) {
            try {
                return toDate(v);
            } catch (GPDataException e) {
                logger.warn("Invalid CPLC date: " + HexUtils.bin2hex(v));
                return "invalid date format";
            }
        }

        public static byte[] today() {
            return fromDate(new GregorianCalendar());
        }

        public static byte[] fromDate(GregorianCalendar d) {
            return HexUtils.hex2bin(String.format("%d%03d", d.get(GregorianCalendar.YEAR) - 2010, d.get(GregorianCalendar.DAY_OF_YEAR)));
        }
    }
}
