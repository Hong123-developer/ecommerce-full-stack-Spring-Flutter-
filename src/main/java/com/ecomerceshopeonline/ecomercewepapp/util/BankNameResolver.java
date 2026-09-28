package com.ecomerceshopeonline.ecomercewepapp.util;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;

/**
 * A Bakong account id ends in the member institution's code, e.g. {@code sok_dara@abaa}.
 * This turns that suffix into something worth showing a customer.
 */
@Component
public class BankNameResolver {

    private static final String FALLBACK = "Bakong KHQR";

    private static final Map<String, String> BANKS = Map.ofEntries(
            Map.entry("abaa", "ABA Bank"),
            Map.entry("bkrt", "Bakong Wallet"),
            Map.entry("aclb", "ACLEDA Bank"),
            Map.entry("wing", "Wing Bank"),
            Map.entry("canb", "Canadia Bank"),
            Map.entry("prbc", "Phillip Bank"),
            Map.entry("sath", "Sathapana Bank"),
            Map.entry("ppcb", "PPCBank"),
            Map.entry("ftbc", "FTB Bank"),
            Map.entry("cpbk", "Campu Bank"),
            Map.entry("jtrb", "J Trust Royal Bank"),
            Map.entry("vdtb", "Vattanac Bank"),
            Map.entry("shin", "Shinhan Bank"),
            Map.entry("wobc", "Woori Bank"),
            Map.entry("btdb", "BRED Bank"),
            Map.entry("cabb", "CAB Bank"),
            Map.entry("amkb", "AMK MFI"),
            Map.entry("lolc", "LOLC"),
            Map.entry("kdsb", "Kredit MFI"),
            Map.entry("mfil", "Mohanokor MFI"),
            Map.entry("devb", "Test Bank"));

    public String resolve(String accountId) {
        if (accountId == null || !accountId.contains("@")) {
            return FALLBACK;
        }
        String code = accountId.substring(accountId.lastIndexOf('@') + 1)
                .toLowerCase(Locale.ROOT);
        if (code.isBlank()) {
            return FALLBACK;
        }
        return BANKS.getOrDefault(code, code.toUpperCase(Locale.ROOT) + " Bank");
    }
}
