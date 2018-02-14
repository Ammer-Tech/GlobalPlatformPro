import apdu4j.HexUtils;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pro.javacard.gp.*;

import java.util.List;

public class TestParseTags {
    final static Logger logger = LoggerFactory.getLogger(TestParseTags.class);

    @Test
    public void testSomething() throws Exception {
        byte[] data = HexUtils.hex2bin("E3464F08A0000000030000009F700101C5039EFE80CF0140CF0141CF0142CF0143CF0180CF0181CF0182CF0183C40BD276000005AAFFCAFE0001CE020001CC08A000000003000000");
        GPRegistry reg = new GPRegistry();
        reg.parse(0x80, data, GPRegistryEntry.Kind.IssuerSecurityDomain, GlobalPlatform.GPSpec.GP22);
    }

    @Test
    public void testOther() throws Exception {
        byte[] data = HexUtils.hex2bin("664C734A06072A864886FC6B01600C060A2A864886FC6B02020101630906072A864886FC6B03640B06092A864886FC6B040215650B06092B8510864864020103660C060A2B060104012A026E0102");
        GPData.pretty_print_card_data(data);
        data = HexUtils.hex2bin("6657735506072A864886FC6B01600B06092A864886FC6B020202630906072A864886FC6B03640B06092A864886FC6B040370640B06092A864886FC6B048000650A06082A864886FC6B0504660C060A2B060104012A026E0103");
        GPData.pretty_print_card_data(data);
    }

    @Test
    public void testOracle() throws Exception {
        byte[] atr = HexUtils.hex2bin("664C734A06072A864886FC6B01600C060A2A864886FC6B02020101630906072A864886FC6B03640B06092A864886FC6B040215650B06092B8510864864020103660C060A2B060104012A026E0102");
        byte[] cplc = HexUtils.hex2bin("664C734A06072A864886FC6B01600C060A2A864886FC6B02020101630906072A864886FC6B03640B06092A864886FC6B040215650B06092B8510864864020103660C060A2B060104012A026E0102");
        byte[] kinfo = HexUtils.hex2bin("664C734A06072A864886FC6B01600C060A2A864886FC6B02020101630906072A864886FC6B03640B06092A864886FC6B040215650B06092B8510864864020103660C060A2B060104012A026E0102");

        //CardOracle.ask("https://javacard.pro/pythia", atr, cplc, kinfo, new AID("A000000151000000"));
    }

    @Test
    public void testRSAKeyTemplate() throws Exception {
        byte[] t = HexUtils.hex2bin("E020C00401018820C00402018820C00403018820C0060170A180A003C00401718010");
        List<GPKey> kl = GPData.get_key_template_list(t);
        GPData.pretty_print_key_template(kl, System.out);
    }
}