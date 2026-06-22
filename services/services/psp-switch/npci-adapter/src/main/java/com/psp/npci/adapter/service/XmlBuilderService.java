package com.psp.npci.adapter.service;

import org.springframework.stereotype.Service;

/**
 * Builds NPCI UPI XML request payloads as plain Java strings.
 *
 * <p>
 * JAXB is intentionally avoided — the XML structures used in this demo are
 * small enough that string building is faster, easier to read, and produces
 * exactly the byte sequence needed without namespace surprises.
 *
 * <p>
 * All XML templates follow the abbreviated UPI 2.0 schema fragments
 * specified in the project requirements.
 */
@Service
public class XmlBuilderService {

    /**
     * Builds the {@code ReqPay} XML payload for a PSP-initiated P2P/P2M payment.
     *
     * @param txnId         NPCI transaction ID (used in the {@code id} attribute)
     * @param msgId         Unique message ID for this attempt
     * @param payerVpa      Payer UPI VPA
     * @param payeeVpa      Payee UPI VPA
     * @param amount        Transaction amount (decimal string, e.g.
     *                      {@code "500.00"})
     * @param encryptedMpin MPIN credential, already encrypted by
     *                      {@code EncryptionService}
     * @param orgId         PSP Org ID (e.g. {@code DEMOPSP}), placed in
     *                      {@code <Psp>}
     * @return fully-formed ReqPay XML string
     */
    public String buildReqPay(String txnId, String msgId, String payerVpa,
            String payeeVpa, String amount,
            String encryptedMpin, String orgId) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<ReqPay>\n" +
                "  <Head msgId=\"" + msgId + "\" orgId=\"" + orgId + "\"/>\n" +
                "  <Txn id=\"" + txnId + "\" type=\"PAY\" note=\"UPI Payment\"\n" +
                "       refId=\"" + msgId + "\" refUrl=\"https://psp.demo/ref/\" ts=\"" + nowIso() + "\">\n" +
                "    <Payer addr=\"" + payerVpa + "\" code=\"" + orgId + "\"\n" +
                "           name=\"Payer\" seqNum=\"1\" type=\"PERSON\">\n" +
                "      <Ac addrType=\"ACCOUNT\">\n" +
                "        <Detail name=\"ACTYPE\" value=\"SAVINGS\"/>\n" +
                "      </Ac>\n" +
                "      <Creds>\n" +
                "        <Cred subType=\"MPIN\" type=\"PIN\">\n" +
                "          <Data code=\"NPCI\" ki=\"20150822\" type=\"ENCRYPTED\">" + encryptedMpin + "</Data>\n" +
                "        </Cred>\n" +
                "      </Creds>\n" +
                "    </Payer>\n" +
                "    <Payees>\n" +
                "      <Payee addr=\"" + payeeVpa + "\" code=\"NPCI\"\n" +
                "             name=\"Payee\" seqNum=\"1\" type=\"ENTITY\">\n" +
                "        <Amount cur=\"INR\" value=\"" + amount + "\"/>\n" +
                "      </Payee>\n" +
                "    </Payees>\n" +
                "  </Txn>\n" +
                "</ReqPay>";
    }

    /**
     * Builds the {@code ReqBalEnq} XML payload for a balance enquiry.
     *
     * @param txnId         NPCI transaction ID
     * @param msgId         Unique message ID for this attempt
     * @param payerVpa      Payer UPI VPA whose balance is being queried
     * @param encryptedMpin MPIN credential for authentication
     * @param orgId         PSP Org ID
     * @return fully-formed ReqBalEnq XML string
     */
    public String buildReqBalEnq(String txnId, String msgId, String payerVpa,
            String encryptedMpin, String orgId) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<ReqBalEnq>\n" +
                "  <Head msgId=\"" + msgId + "\" orgId=\"" + orgId + "\"/>\n" +
                "  <Txn id=\"" + txnId + "\" type=\"BALANCEENQUIRY\" ts=\"" + nowIso() + "\">\n" +
                "    <Payer addr=\"" + payerVpa + "\" code=\"" + orgId + "\"\n" +
                "           name=\"Payer\" type=\"PERSON\">\n" +
                "      <Creds>\n" +
                "        <Cred subType=\"MPIN\" type=\"PIN\">\n" +
                "          <Data code=\"NPCI\" ki=\"20150822\" type=\"ENCRYPTED\">" + encryptedMpin + "</Data>\n" +
                "        </Cred>\n" +
                "      </Creds>\n" +
                "    </Payer>\n" +
                "  </Txn>\n" +
                "</ReqBalEnq>";
    }

    /**
     * Builds the standard UPI Ack XML sent back to NPCI immediately upon
     * receiving any inbound webhook (before async processing starts).
     *
     * @param txnId transaction ID being acknowledged
     * @return Ack XML string
     */
    public String buildAck(String txnId) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<Ack api=\"RespAck\" err=\"\" ts=\"" + nowIso() + "\"\n" +
                "     txnId=\"" + txnId + "\"/>";
    }

    /**
     * Builds the {@code ReqVpa} XML payload for a VPA verification.
     *
     * @param txnId         NPCI transaction ID
     * @param msgId         Unique message ID
     * @param vpa           VPA to verify
     * @param orgId         PSP Org ID
     * @return fully-formed ReqVpa XML string
     */
    public String buildReqVpa(String txnId, String msgId, String vpa, String orgId) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<ReqVpa>\n" +
                "  <Head msgId=\"" + msgId + "\" orgId=\"" + orgId + "\"/>\n" +
                "  <Txn id=\"" + txnId + "\" type=\"REQ_VPA\" ts=\"" + nowIso() + "\">\n" +
                "    <Payee addr=\"" + vpa + "\"/>\n" +
                "  </Txn>\n" +
                "</ReqVpa>";
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private String nowIso() {
        return java.time.Instant.now().toString();
    }
}
