package com.pspswitch.npciresponse.parser;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

/**
 * Parses UPI XML callbacks from NPCI.
 *
 * <p>Supports parsing of:
 * <ul>
 *   <li>RespPay — payment response (result, errCode, msgId, approvalNum)</li>
 *   <li>RespBalEnq — balance enquiry response (result, balance, currency)</li>
 *   <li>ReqPay (inbound collect) — payerVpa, payeeVpa, amount, txnType</li>
 * </ul>
 *
 * <p>Uses Java's built-in DOM parser (javax.xml) — no additional XML library needed.
 * All parse methods are null-safe and return empty string on failure.
 */
@Slf4j
@Component
public class NpciXmlParser {

    // ═══════════════════════════════════════════════════════════════════
    // RespPay parsing
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Extracts the transaction result from a RespPay XML.
     * Looks for: {@code <Resp result="SUCCESS"/>} or {@code result} attribute on root.
     */
    public String parseResult(String xml) {
        try {
            Document doc = parse(xml);
            // Try <Resp result="..."/>
            NodeList respNodes = doc.getElementsByTagName("Resp");
            if (respNodes.getLength() > 0) {
                String result = ((Element) respNodes.item(0)).getAttribute("result");
                if (result != null && !result.isBlank()) return result.toUpperCase();
            }
            // Fallback: root element result attribute
            String rootResult = doc.getDocumentElement().getAttribute("result");
            return rootResult != null ? rootResult.toUpperCase() : "";
        } catch (Exception e) {
            log.warn("[XML-PARSER] Failed to parse result: {}", e.getMessage());
            return "";
        }
    }

    /**
     * Extracts the NPCI error code from a RespPay XML.
     * Looks for: {@code <Resp errCode="ZM"/>}
     */
    public String parseErrCode(String xml) {
        try {
            Document doc = parse(xml);
            NodeList respNodes = doc.getElementsByTagName("Resp");
            if (respNodes.getLength() > 0) {
                return ((Element) respNodes.item(0)).getAttribute("errCode");
            }
            return doc.getDocumentElement().getAttribute("errCode");
        } catch (Exception e) {
            log.warn("[XML-PARSER] Failed to parse errCode: {}", e.getMessage());
            return "";
        }
    }

    /**
     * Extracts msgId from a UPI XML message.
     * Looks for: {@code <Head msgId="..."/>}
     */
    public String parseMsgId(String xml) {
        try {
            Document doc = parse(xml);
            NodeList headNodes = doc.getElementsByTagName("Head");
            if (headNodes.getLength() > 0) {
                return ((Element) headNodes.item(0)).getAttribute("msgId");
            }
            return "";
        } catch (Exception e) {
            log.warn("[XML-PARSER] Failed to parse msgId: {}", e.getMessage());
            return "";
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // RespBalEnq parsing
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Extracts the account balance from a RespBalEnq XML.
     * Looks for: {@code <BalDetail settlementAmount="..."/>}
     */
    public String parseBalance(String xml) {
        try {
            Document doc = parse(xml);
            // Try BalDetail
            NodeList bal = doc.getElementsByTagName("BalDetail");
            if (bal.getLength() > 0) {
                String amt = ((Element) bal.item(0)).getAttribute("settlementAmount");
                if (amt != null && !amt.isBlank()) return amt;
            }
            return "";
        } catch (Exception e) {
            log.warn("[XML-PARSER] Failed to parse balance: {}", e.getMessage());
            return "";
        }
    }

    /**
     * Extracts the currency from a RespBalEnq XML.
     */
    public String parseCurrency(String xml) {
        try {
            Document doc = parse(xml);
            NodeList bal = doc.getElementsByTagName("BalDetail");
            if (bal.getLength() > 0) {
                String cur = ((Element) bal.item(0)).getAttribute("currency");
                return (cur != null && !cur.isBlank()) ? cur : "INR";
            }
            return "INR";
        } catch (Exception e) {
            return "INR";
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Inbound ReqPay (COLLECT) parsing
    // ═══════════════════════════════════════════════════════════════════

    /** Extracts payer VPA from inbound ReqPay XML. */
    public String parsePayerVpa(String xml) {
        try {
            Document doc = parse(xml);
            NodeList payer = doc.getElementsByTagName("Payer");
            if (payer.getLength() > 0) {
                return ((Element) payer.item(0)).getAttribute("addr");
            }
            return "";
        } catch (Exception e) {
            return "";
        }
    }

    /** Extracts payee VPA from inbound ReqPay XML. */
    public String parsePayeeVpa(String xml) {
        try {
            Document doc = parse(xml);
            NodeList payee = doc.getElementsByTagName("Payee");
            if (payee.getLength() > 0) {
                return ((Element) payee.item(0)).getAttribute("addr");
            }
            return "";
        } catch (Exception e) {
            return "";
        }
    }

    /** Extracts transaction amount from XML. */
    public String parseAmount(String xml) {
        try {
            Document doc = parse(xml);
            NodeList amount = doc.getElementsByTagName("Amount");
            if (amount.getLength() > 0) {
                return ((Element) amount.item(0)).getAttribute("value");
            }
            return "";
        } catch (Exception e) {
            return "";
        }
    }

    /** Extracts txnType from the Txn element. */
    public String parseTxnType(String xml) {
        try {
            Document doc = parse(xml);
            NodeList txn = doc.getElementsByTagName("Txn");
            if (txn.getLength() > 0) {
                return ((Element) txn.item(0)).getAttribute("type");
            }
            return "";
        } catch (Exception e) {
            return "";
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Helper
    // ═══════════════════════════════════════════════════════════════════

    private Document parse(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        // Disable external entity processing to prevent XXE
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setExpandEntityReferences(false);
        factory.setNamespaceAware(true);

        DocumentBuilder builder = factory.newDocumentBuilder();
        byte[] bytes = xml.getBytes(StandardCharsets.UTF_8);
        return builder.parse(new ByteArrayInputStream(bytes));
    }
}
