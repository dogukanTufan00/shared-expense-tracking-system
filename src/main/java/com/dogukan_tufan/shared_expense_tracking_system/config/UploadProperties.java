package com.dogukan_tufan.shared_expense_tracking_system.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

// Binds app.uploads.* from application.yaml. dir is the folder on the local disk where
// attachment files (receipts, bills, transfer slips) are stored.
@Getter
@Setter
@ConfigurationProperties(prefix = "app.uploads")
public class UploadProperties {

    private String dir;
}
