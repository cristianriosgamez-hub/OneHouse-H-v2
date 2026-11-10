package com.onehouse.app.feature.backup

data class BackupSummary(
    val createdAtMillis: Long,
    val appVersion: String,
    val preferencesFiles: Int,
    val knxEntries: Int,
    val knxActiveAddresses: Int,
    val climateScheduleEvents: Int
)

data class BackupValidation(
    val schemaVersion: Int,
    val compatiblePreferenceFiles: Int,
    val integrityVerified: Boolean,
    val warnings: List<String>
) {
    val isClean: Boolean get() = integrityVerified && warnings.isEmpty()
}

data class BackupPreview(
    val summary: BackupSummary,
    val validation: BackupValidation,
    val rawJson: String
)

sealed interface BackupOperationResult {
    data class Success(val message: String) : BackupOperationResult
    data class Error(val message: String) : BackupOperationResult
}
