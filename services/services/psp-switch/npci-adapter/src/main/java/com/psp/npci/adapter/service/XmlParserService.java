package com.psp.npci.adapter.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses NPCI XML responses using regex-based extraction.
 *
 * <p>
 * JAXB is intentionally avoided. The XML response messages are small,
 * well-structured, and their schemas are fixed by the NPCI spec. Regex
 * extraction is faster, avoids classpath conflicts, and is sufficient for
 * the fields the Adapter needs.
 *
 * <h2>Contract</h2>
 * <p>
 * All {@code parse*} methods are forgiving — they never throw. Missing
 * attributes return an empty string so callers can always rely on a non-null
 * return value.
 */
@Slf4j
@Service
public class XmlParserService {

    // ─────────────────────────────────────────────────────────────────────────
    // Compiled patterns (reused across calls)
    // ─────────────────────────────────────────────────────────────────────────

    private static final Pattern RESULT_PATTERN = Pattern.compile("result\\s*=\\s*\"([^\"]+)\"",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ERR_CODE_PATTERN = Pattern.compile("errCode\\s*=\\s*\"([^\"]*)\"",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern MSG_ID_PATTERN = Pattern.compile("msgId\\s*=\\s*\"([^\"]+)\"",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TXN_ID_PATTERN = Pattern.compile("id\\s*=\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern SETT_AMOUNT_PATTERN = Pattern.compile("settAmount\\s*=\\s*\"([^\"]+)\"",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SETT_CURR_PATTERN = Pattern.compile("settCurrency\\s*=\\s*\"([^\"]+)\"",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PAYER_ADDR_PATTERN = Pattern.compile("Payer[^>]*addr\\s*=\\s*\"([^\"]+)\"",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PAYEE_ADDR_PATTERN = Pattern.compile("Payee[^>]*addr\\s*=\\s*\"([^\"]+)\"",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("Amount[^/]*/?>.*?value\\s*=\\s*\"([^\"]+)\"",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern TXN_TYPE_PATTERN = Pattern.compile("<Txn[^>]*type\\s*=\\s*\"([^\"]+)\"",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PAYEE_NAME_PATTERN = Pattern.compile("Payee[^>]*name\\s*=\\s*\"([^\"]+)\"",
            Pattern.CASE_INSENSITIVE);

    // ─────────────────────────────────────────────────────────────────────────
    // RespPay parsing
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Extracts {@code result} from a {@code RespPay} XML body.
     * E.g. {@code result="SUCCESS"} → {@code "SUCCESS"}.
     */
    public String parseResult(String xml) {
        return extract(xml, RESULT_PATTERN, "result");
    }

    /**
     * Extracts {@code errCode} from a {@code RespPay} XML body.
     * Returns empty string when no error code is present.
     */
    public String parseErrCode(String xml) {
        return extract(xml, ERR_CODE_PATTERN, "errCode");
    }

    /**
     * Extracts {@code msgId} from an XML head element.
     * E.g. {@code msgId="abc-123"} → {@code "abc-123"}.
     */
    public String parseMsgId(String xml) {
        return extract(xml, MSG_ID_PATTERN, "msgId");
    }

    /**
     * Extracts the transaction {@code id} attribute from a {@code <Txn>} element.
     * E.g. {@code id="txn-001"} → {@code "txn-001"}.
     */
    public String parseTxnId(String xml) {
        return extract(xml, TXN_ID_PATTERN, "txnId");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RespBalEnq parsing
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Extracts {@code settAmount} from a {@code RespBalEnq} XML body.
     * E.g. {@code settAmount="25000.00"} → {@code "25000.00"}.
     */
    public String parseSettAmount(String xml) {
        return extract(xml, SETT_AMOUNT_PATTERN, "settAmount");
    }

    /**
     * Extracts {@code settCurrency} from a {@code RespBalEnq} XML body.
     * E.g. {@code settCurrency="INR"} → {@code "INR"}.
     */
    public String parseSettCurrency(String xml) {
        return extract(xml, SETT_CURR_PATTERN, "settCurrency");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Inbound ReqPay (collect) parsing
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Extracts the payer VPA ({@code addr} attribute of the {@code <Payer>}
     * element).
     */
    public String parsePayerVpa(String xml) {
        return extract(xml, PAYER_ADDR_PATTERN, "payerVpa");
    }

    /**
     * Extracts the payee VPA ({@code addr} attribute of the {@code <Payee>}
     * element).
     */
    public String parsePayeeVpa(String xml) {
        return extract(xml, PAYEE_ADDR_PATTERN, "payeeVpa");
    }

    /**
     * Extracts the transaction amount from an {@code <Amount>} element.
     */
    public String parseAmount(String xml) {
        return extract(xml, AMOUNT_PATTERN, "amount");
    }

    /**
     * Extracts the {@code type} attribute from the {@code <Txn>} element.
     * For inbound collect requests this is typically {@code COLLECT} or
     * {@code CREDIT}.
     */
    public String parseTxnType(String xml) {
        return extract(xml, TXN_TYPE_PATTERN, "txnType");
    }

    /**
     * Extracts the payee name from the {@code <Payee>} element (e.g. RespVpa).
     */
    public String parsePayeeName(String xml) {
        return extract(xml, PAYEE_NAME_PATTERN, "payeeName");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helper
    // ─────────────────────────────────────────────────────────────────────────

    private String extract(String xml, Pattern pattern, String fieldName) {
        if (xml == null || xml.isBlank()) {
            log.warn("[XML-PARSER] Cannot parse '{}' — xml is null/blank", fieldName);
            return "";
        }
        Matcher m = pattern.matcher(xml);
        if (m.find()) {
            String value = m.group(1);
            log.debug("[XML-PARSER] Extracted {}={}", fieldName, value);
            return value != null ? value : "";
        }
        log.debug("[XML-PARSER] Field '{}' not found in XML", fieldName);
        return "";
    }
}
