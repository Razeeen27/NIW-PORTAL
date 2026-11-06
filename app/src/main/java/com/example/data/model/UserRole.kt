package com.example.data.model

enum class UserRole(val displayName: String, val description: String) {
    EMPLOYEE("Gate Operator / Employee", "Record incoming vehicles, capture photos, view inward entries"),
    MANAGER_ADMIN("Facility Manager / Admin", "Full access: reports, analytics, entry editing, cloud sync management"),
    VENDOR("Vendor / Transporter", "Read-only access to customer inward receipts & status")
}
