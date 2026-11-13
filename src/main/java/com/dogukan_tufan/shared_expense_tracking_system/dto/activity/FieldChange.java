package com.dogukan_tufan.shared_expense_tracking_system.dto.activity;

// One stored change inside an activity row, e.g. field "amount", old "30000.00", new "31000.00".
// This is the shape kept in the changes JSON column. Values are raw: person fields hold the user id
// as text, and the read endpoint replaces those ids with names (see FieldChangeResponse).
// userId is set only for "share" changes: it says whose owed amount changed. It is null for every
// other field. Rows stored before this component existed have no userId in their JSON; Jackson reads
// the missing value as null, so those old rows still load.
public record FieldChange(String field, String oldValue, String newValue, Long userId) {
}
