package com.example.data.model

data class SenderFilter(
    val id: String,
    val name: String,
    val category: String, // "Top MFS", "Payment Gateways", "Top Banks"
    val matchSenders: List<String>, // sender addresses to match from SMS
    val description: String,
    val defaultEnabled: Boolean = false
)

object PredefinedSenders {
    const val CATEGORY_MFS = "Top MFS"
    const val CATEGORY_GATEWAYS = "Payment Gateways"
    const val CATEGORY_BANKS = "Top Banks"

    val DEFAULT_ENABLED_IDS = setOf("bKash", "Nagad")

    val ALL: List<SenderFilter> = listOf(
        // Top MFS
        SenderFilter(
            id = "bKash",
            name = "bKash",
            category = CATEGORY_MFS,
            matchSenders = listOf("bKash", "16247"),
            description = "bKash mobile financial services",
            defaultEnabled = true
        ),
        SenderFilter(
            id = "Nagad",
            name = "Nagad",
            category = CATEGORY_MFS,
            matchSenders = listOf("Nagad", "16167"),
            description = "Nagad Post Office MFS",
            defaultEnabled = true
        ),
        SenderFilter(
            id = "16216",
            name = "16216 (Rocket)",
            category = CATEGORY_MFS,
            matchSenders = listOf("16216", "ROCKET", "Rocket"),
            description = "DBBL Rocket mobile banking",
            defaultEnabled = false
        ),
        SenderFilter(
            id = "upay",
            name = "upay",
            category = CATEGORY_MFS,
            matchSenders = listOf("upay", "UPAY", "16268"),
            description = "UCB Upay digital wallet",
            defaultEnabled = false
        ),
        SenderFilter(
            id = "Cellfin",
            name = "Cellfin",
            category = CATEGORY_MFS,
            matchSenders = listOf("Cellfin", "CELLFIN"),
            description = "Islami Bank Cellfin app",
            defaultEnabled = false
        ),
        SenderFilter(
            id = "tap",
            name = "tap",
            category = CATEGORY_MFS,
            matchSenders = listOf("tap", "TAP"),
            description = "Trust Axiata Pay",
            defaultEnabled = false
        ),
        SenderFilter(
            id = "mCash",
            name = "mCash",
            category = CATEGORY_MFS,
            matchSenders = listOf("mCash", "MCASH"),
            description = "Islami Bank mCash",
            defaultEnabled = false
        ),
        SenderFilter(
            id = "OK Wallet",
            name = "OK Wallet",
            category = CATEGORY_MFS,
            matchSenders = listOf("OK Wallet", "OKWallet", "OK_WALLET", "ONEBANK"),
            description = "ONE Bank OK Wallet",
            defaultEnabled = false
        ),

        // Payment Gateways
        SenderFilter(
            id = "TallyKhata",
            name = "TallyKhata",
            category = CATEGORY_GATEWAYS,
            matchSenders = listOf("TallyKhata", "TALLYKHATA"),
            description = "TallyKhata merchant wallet",
            defaultEnabled = false
        ),
        SenderFilter(
            id = "TallyPay",
            name = "TallyPay",
            category = CATEGORY_GATEWAYS,
            matchSenders = listOf("TallyPay", "TALLYPAY", "Tally Pay"),
            description = "TallyPay QR digital payments",
            defaultEnabled = false
        ),
        SenderFilter(
            id = "DGePAY",
            name = "DGePAY",
            category = CATEGORY_GATEWAYS,
            matchSenders = listOf("DGePAY", "DGEPAY"),
            description = "DGePAY payment gateway",
            defaultEnabled = false
        ),
        SenderFilter(
            id = "UddoktaPay",
            name = "UddoktaPay",
            category = CATEGORY_GATEWAYS,
            matchSenders = listOf("UddoktaPay", "UDDOKTAPAY"),
            description = "UddoktaPay payment gateway",
            defaultEnabled = false
        ),
        SenderFilter(
            id = "Pathao",
            name = "Pathao",
            category = CATEGORY_GATEWAYS,
            matchSenders = listOf("Pathao", "PATHAO"),
            description = "Pathao merchant & rider payment",
            defaultEnabled = false
        ),
        SenderFilter(
            id = "Pathao Pay",
            name = "Pathao Pay",
            category = CATEGORY_GATEWAYS,
            matchSenders = listOf("Pathao Pay", "PathaoPay", "PATHAOPAY"),
            description = "Pathao Pay digital wallet",
            defaultEnabled = false
        ),

        // Top Banks
        SenderFilter(
            id = "BRACBANK",
            name = "BRACBANK",
            category = CATEGORY_BANKS,
            matchSenders = listOf("BRACBANK"),
            description = "BRAC Bank Limited alerts",
            defaultEnabled = false
        ),
        SenderFilter(
            id = "IBBL",
            name = "IBBL",
            category = CATEGORY_BANKS,
            matchSenders = listOf("IBBL", "IslamiBank"),
            description = "Islami Bank Bangladesh Limited",
            defaultEnabled = false
        ),
        SenderFilter(
            id = "Trust Bank",
            name = "Trust Bank",
            category = CATEGORY_BANKS,
            matchSenders = listOf("Trust Bank", "TrustBank", "TRUSTBANK"),
            description = "Trust Bank Limited alerts",
            defaultEnabled = false
        ),
        SenderFilter(
            id = "MidlandBank",
            name = "MidlandBank",
            category = CATEGORY_BANKS,
            matchSenders = listOf("MidlandBank", "Midland Bank", "MDB"),
            description = "Midland Bank Limited",
            defaultEnabled = false
        ),
        SenderFilter(
            id = "CITY BANK",
            name = "CITY BANK",
            category = CATEGORY_BANKS,
            matchSenders = listOf("CITY BANK", "CityBank", "CITYBANK", "Citytouch"),
            description = "The City Bank Limited",
            defaultEnabled = false
        ),
        SenderFilter(
            id = "DBBL",
            name = "DBBL",
            category = CATEGORY_BANKS,
            matchSenders = listOf("DBBL", "Dutch-Bangla"),
            description = "Dutch-Bangla Bank Limited",
            defaultEnabled = false
        ),
        SenderFilter(
            id = "EBL",
            name = "EBL",
            category = CATEGORY_BANKS,
            matchSenders = listOf("EBL", "EasternBank"),
            description = "Eastern Bank Limited",
            defaultEnabled = false
        ),
        SenderFilter(
            id = "BANK ASIA",
            name = "BANK ASIA",
            category = CATEGORY_BANKS,
            matchSenders = listOf("BANK ASIA", "BankAsia", "BANKASIA"),
            description = "Bank Asia Limited alerts",
            defaultEnabled = false
        ),
        SenderFilter(
            id = "AB BANK",
            name = "AB BANK",
            category = CATEGORY_BANKS,
            matchSenders = listOf("AB BANK", "ABBANK", "ABBank"),
            description = "AB Bank Limited alerts",
            defaultEnabled = false
        ),
        SenderFilter(
            id = "Prime Bank",
            name = "Prime Bank",
            category = CATEGORY_BANKS,
            matchSenders = listOf("Prime Bank", "PrimeBank", "PRIMEBANK"),
            description = "Prime Bank Limited alerts",
            defaultEnabled = false
        ),
        SenderFilter(
            id = "MTB",
            name = "MTB",
            category = CATEGORY_BANKS,
            matchSenders = listOf("MTB", "MutualTrust"),
            description = "Mutual Trust Bank Limited",
            defaultEnabled = false
        ),
        SenderFilter(
            id = "Dhaka Bank",
            name = "Dhaka Bank",
            category = CATEGORY_BANKS,
            matchSenders = listOf("Dhaka Bank", "DhakaBank", "DHAKABANK"),
            description = "Dhaka Bank Limited alerts",
            defaultEnabled = false
        ),
        SenderFilter(
            id = "NRB Bank",
            name = "NRB Bank",
            category = CATEGORY_BANKS,
            matchSenders = listOf("NRB Bank", "NRBBank", "NRBBANK"),
            description = "NRB Bank Limited alerts",
            defaultEnabled = false
        ),
        SenderFilter(
            id = "Sonali Bank",
            name = "Sonali Bank",
            category = CATEGORY_BANKS,
            matchSenders = listOf("Sonali Bank", "SonaliBank", "SONALIBANK", "Sonali"),
            description = "Sonali Bank Limited & NPSB alerts",
            defaultEnabled = false
        )
    )

    fun matchesSender(rawSender: String, enabledFilterIds: Set<String>): Boolean {
        val trimmed = rawSender.trim()
        val normalized = trimmed.lowercase()

        for (filter in ALL) {
            if (enabledFilterIds.contains(filter.id)) {
                if (filter.matchSenders.any { it.lowercase() == normalized } ||
                    filter.name.lowercase() == normalized ||
                    filter.id.lowercase() == normalized) {
                    return true
                }
            }
        }

        // Also check if custom sender added directly equals rawSender
        return enabledFilterIds.any { it.trim().lowercase() == normalized }
    }
}
