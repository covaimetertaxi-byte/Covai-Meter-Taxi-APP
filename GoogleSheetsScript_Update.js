/**
 * Trusty Yellow Cab - Google Sheets Web App Sync Script (V13 Update)
 * SINGLE DEVICE LOGIN ENFORCEMENT & CLOUD SYNC
 * 
 * ============================================================================
 * WHAT'S NEW IN V13:
 * 1. Single Device Login Enforcement:
 *    - 1 Driver ID can only be logged in on 1 active device at a time.
 *    - If Driver A is logged in on Phone 1, Phone 2 is BLOCKED with:
 *      "This Driver ID is already logged in on another device. Please log out from that device first."
 *    - When Driver A logs out on Phone 1, the device session is cleared,
 *      allowing Phone 2 (or any other device) to log in immediately.
 * 2. Admin Quick-Unlock:
 *    - If a driver's phone is lost or damaged, Admin opens the Google Sheet,
 *      goes to "Drivers" tab, and clears the "Active Device ID" column or sets
 *      "Login Status" to "Logged Out". The driver can immediately log in on a new device!
 * 3. Automatic Column Upgrade:
 *    - Automatically detects existing columns and adds new columns ("Driver ID",
 *      "PIN", "Login Status", "Active Device ID") without overwriting existing data.
 * ============================================================================
 * 
 * BACKEND TABLE COLUMNS ("Drivers" Sheet):
 * ----------------------------------------------------------------------------
 * Col 1: Driver ID              (e.g., DRV-001, 101, or vehicle registration)
 * Col 2: PIN                    (e.g., 1234 - driver's PIN or last 4 digits of vehicle)
 * Col 3: Driver Name            (e.g., RAJESH KUMAR)
 * Col 4: Vehicle Number         (e.g., TN 38 AB 1234)
 * Col 5: Vehicle Category       (e.g., Mini, Sedan, SUV)
 * Col 6: Vehicle Model          (e.g., Swift Dzire)
 * Col 7: Driver Mobile          (e.g., 9876543210)
 * Col 8: Login Status           (Logged In / Logged Out)
 * Col 9: Active Device ID       (Unique UUID of active device; empty when logged out)
 * Col 10: Last Active Time (IST)(e.g., 23 Sep 2026, 11:30:00 AM)
 * ============================================================================
 * 
 * INSTRUCTIONS FOR DEPLOYMENT:
 * 1. Open your Google Sheet: https://sheets.google.com
 * 2. Click "Extensions" -> "Apps Script".
 * 3. Replace all code in Code.gs with this entire script.
 * 4. Click the disk icon ("Save project").
 * 5. Click "Deploy" -> "Manage deployments" -> edit to "New version" OR
 *    "Deploy" -> "New deployment" -> Web App.
 *    - Execute as: "Me"
 *    - Who has access: "Anyone"
 * 6. Copy the generated Web App URL and paste it into the app's settings or
 *    HARDCODED_SHEETS_URL in GoogleSheetsSyncManager.kt!
 */

function doPost(e) {
  try {
    var jsonString = e.postData.contents;
    var data = JSON.parse(jsonString);
    var sheetApp = SpreadsheetApp.getActiveSpreadsheet();
    
    // Create or get the required sheets
    var driversSheet = getOrCreateSheet(sheetApp, "Drivers");
    var tripsSheet = getOrCreateSheet(sheetApp, "Trips");
    
    // Define standard headers
    var driverHeaders = [
      "Driver ID",
      "PIN",
      "Driver Name", 
      "Vehicle Number", 
      "Vehicle Category", 
      "Vehicle Model", 
      "Driver Mobile", 
      "Login Status",
      "Active Device ID", 
      "Last Active Time (IST)"
    ];
    
    var tripHeaders = [
      "Trip ID Code", 
      "Date (IST)", 
      "Driver Name", 
      "Vehicle Number", 
      "Vehicle Category", 
      "Vehicle Model", 
      "Start Time (IST)", 
      "End Time (IST)", 
      "Distance (KM)", 
      "Duration", 
      "Waiting Time", 
      "Total Fare (INR)", 
      "Base Fare (INR)", 
      "Per KM Fare (INR)", 
      "Waiting Charge/Min", 
      "Minimum Fare Bound", 
      "Night Charge %", 
      "CC Commission (INR)", 
      "Customer Mobile", 
      "Start Location", 
      "End Location", 
      "Is Package Meter?", 
      "Package Name", 
      "Package Base Fare", 
      "Included KM", 
      "Included Minutes", 
      "Extra KM Rate", 
      "Extra Time Rate", 
      "Package Waiting/Min", 
      "Device ID"
    ];

    initHeadersWithUpgrade(driversSheet, driverHeaders);
    initHeadersWithUpgrade(tripsSheet, tripHeaders);

    var response = {};
    var timestampStr = data.dateStr || new Date().toLocaleString("en-US", {timeZone: "Asia/Kolkata"});

    // =========================================================================
    // 1. VERIFY LOGIN / LOGIN REQUEST (Single Device Enforcement)
    // =========================================================================
    if (data.type === "verify_login" || data.type === "login") {
      var driverIdInput = String(data.driverId || "").trim();
      var pinInput = String(data.pin || "").trim();
      var incomingDeviceId = String(data.deviceId || "").trim();
      var driverMobile = String(data.driverMobile || "").trim();
      var vehicleNumber = String(data.vehicleNumber || "").trim().toUpperCase();
      var driverName = String(data.driverName || "").trim().toUpperCase();

      var colMap = getHeaderMap(driversSheet);
      var rows = driversSheet.getDataRange().getValues();
      var matchedRowIndex = -1;
      var matchedRowData = null;

      // Find driver by Driver ID, Vehicle Number, or Mobile Number (skip header row 0)
      for (var i = 1; i < rows.length; i++) {
        var row = rows[i];
        var sDriverId = String(row[colMap["Driver ID"] - 1] || "").trim();
        var sVehicle = String(row[colMap["Vehicle Number"] - 1] || "").trim().toUpperCase();
        var sMobile = String(row[colMap["Driver Mobile"] - 1] || "").trim();

        var matchById = (driverIdInput !== "" && sDriverId.toLowerCase() === driverIdInput.toLowerCase());
        var matchByVehicle = (driverIdInput !== "" && sVehicle.replace(/\s+/g, "").toUpperCase() === driverIdInput.replace(/\s+/g, "").toUpperCase()) ||
                             (vehicleNumber !== "" && sVehicle === vehicleNumber);
        var matchByMobile = (driverIdInput !== "" && sMobile === driverIdInput) ||
                            (driverMobile !== "" && sMobile === driverMobile);

        if (matchById || matchByVehicle || matchByMobile) {
          matchedRowIndex = i + 1; // 1-based index in Google Sheets
          matchedRowData = row;
          break;
        }
      }

      if (matchedRowIndex !== -1 && matchedRowData) {
        var sheetPin = String(matchedRowData[colMap["PIN"] - 1] || "").trim();
        var sheetVehicle = String(matchedRowData[colMap["Vehicle Number"] - 1] || "").trim().toUpperCase();
        var sheetStatus = String(matchedRowData[colMap["Login Status"] - 1] || "").trim();
        var sheetActiveDevice = String(matchedRowData[colMap["Active Device ID"] - 1] || "").trim();
        var resolvedDriverId = String(matchedRowData[colMap["Driver ID"] - 1] || driverIdInput).trim();
        var resolvedDriverName = String(matchedRowData[colMap["Driver Name"] - 1] || driverName).trim();
        var resolvedCategory = String(matchedRowData[colMap["Vehicle Category"] - 1] || "Mini").trim();
        var resolvedModel = String(matchedRowData[colMap["Vehicle Model"] - 1] || "").trim();
        var resolvedMobile = String(matchedRowData[colMap["Driver Mobile"] - 1] || driverMobile).trim();

        // 1. PIN Verification
        if (pinInput !== "") {
          var vehiclePin = extractDigits(sheetVehicle);
          if (vehiclePin.length >= 4) {
            vehiclePin = vehiclePin.substring(vehiclePin.length - 4);
          }
          var isPinValid = (sheetPin !== "" && sheetPin === pinInput) ||
                           (vehiclePin !== "" && vehiclePin === pinInput);
          if (!isPinValid) {
            return returnJson({
              status: "error",
              code: "INVALID_PIN",
              message: "Incorrect PIN for Driver ID '" + (driverIdInput || resolvedDriverId) + "'. Please check and try again."
            });
          }
        }

        // 2. SINGLE DEVICE LOGIN ENFORCEMENT
        var isAlreadyLoggedIn = (sheetStatus === "Logged In" || sheetStatus === "Online");
        if (isAlreadyLoggedIn && sheetActiveDevice !== "" && incomingDeviceId !== "" && sheetActiveDevice !== incomingDeviceId) {
          // BLOCKED: Active on another phone!
          return returnJson({
            status: "error",
            code: "DEVICE_ALREADY_LOGGED_IN",
            message: "This Driver ID is already logged in on another device. Please log out from that device first or contact admin."
          });
        }

        // 3. ALLOW LOGIN: Update row with new active device and timestamp
        driversSheet.getRange(matchedRowIndex, colMap["Login Status"]).setValue("Logged In");
        driversSheet.getRange(matchedRowIndex, colMap["Active Device ID"]).setValue(incomingDeviceId);
        driversSheet.getRange(matchedRowIndex, colMap["Last Active Time (IST)"]).setValue(timestampStr);
        if (driverName !== "") driversSheet.getRange(matchedRowIndex, colMap["Driver Name"]).setValue(driverName);
        if (data.vehicleCategory) driversSheet.getRange(matchedRowIndex, colMap["Vehicle Category"]).setValue(data.vehicleCategory);
        if (data.vehicleModel) driversSheet.getRange(matchedRowIndex, colMap["Vehicle Model"]).setValue(data.vehicleModel);

        return returnJson({
          status: "success",
          code: "LOGIN_SUCCESS",
          message: "Login verified successfully",
          driverId: resolvedDriverId,
          driverName: resolvedDriverName,
          vehicleNumber: sheetVehicle,
          vehicleCategory: resolvedCategory,
          vehicleModel: resolvedModel,
          driverMobile: resolvedMobile
        });

      } else {
        // Driver ID not found in sheet
        if (data.type === "login" && driverName !== "" && vehicleNumber !== "") {
          // Register new driver row if full profile was supplied
          driversSheet.appendRow([
            driverIdInput || vehicleNumber,
            pinInput || extractDigits(vehicleNumber).slice(-4),
            driverName,
            vehicleNumber,
            data.vehicleCategory || "Mini",
            data.vehicleModel || "N/A",
            driverMobile,
            "Logged In",
            incomingDeviceId,
            timestampStr
          ]);
          formatTableRows(driversSheet, driverHeaders.length);

          return returnJson({
            status: "success",
            code: "NEW_DRIVER_REGISTERED",
            message: "New driver profile registered successfully",
            driverId: driverIdInput || vehicleNumber,
            driverName: driverName,
            vehicleNumber: vehicleNumber,
            vehicleCategory: data.vehicleCategory || "Mini",
            vehicleModel: data.vehicleModel || "N/A",
            driverMobile: driverMobile
          });
        } else {
          return returnJson({
            status: "error",
            code: "DRIVER_NOT_FOUND",
            message: "Driver ID '" + driverIdInput + "' not found in Drivers database."
          });
        }
      }

    // =========================================================================
    // 2. LOGOUT REQUEST (Clears Device Session to Allow Other Devices)
    // =========================================================================
    } else if (data.type === "logout") {
      var logoutDriverId = String(data.driverId || "").trim();
      var logoutMobile = String(data.driverMobile || "").trim();
      var logoutVehicle = String(data.vehicleNumber || "").trim().toUpperCase();
      var logoutDeviceId = String(data.deviceId || "").trim();

      var colMap = getHeaderMap(driversSheet);
      var rows = driversSheet.getDataRange().getValues();
      var matchedRowIndex = -1;

      for (var i = 1; i < rows.length; i++) {
        var row = rows[i];
        var sDriverId = String(row[colMap["Driver ID"] - 1] || "").trim();
        var sVehicle = String(row[colMap["Vehicle Number"] - 1] || "").trim().toUpperCase();
        var sMobile = String(row[colMap["Driver Mobile"] - 1] || "").trim();
        var sDeviceId = String(row[colMap["Active Device ID"] - 1] || "").trim();

        if ((logoutDriverId !== "" && sDriverId.toLowerCase() === logoutDriverId.toLowerCase()) ||
            (logoutVehicle !== "" && sVehicle === logoutVehicle) ||
            (logoutMobile !== "" && sMobile === logoutMobile) ||
            (logoutDeviceId !== "" && sDeviceId === logoutDeviceId)) {
          matchedRowIndex = i + 1;
          break;
        }
      }

      if (matchedRowIndex !== -1) {
        // Clear Active Device ID and mark as Logged Out
        driversSheet.getRange(matchedRowIndex, colMap["Login Status"]).setValue("Logged Out");
        driversSheet.getRange(matchedRowIndex, colMap["Active Device ID"]).setValue("");
        driversSheet.getRange(matchedRowIndex, colMap["Last Active Time (IST)"]).setValue(timestampStr);
      }

      return returnJson({
        status: "success",
        code: "LOGOUT_SUCCESS",
        message: "Driver logged out successfully. Session cleared."
      });

    // =========================================================================
    // 3. TRIP RECORD SYNC REQUEST
    // =========================================================================
    } else if (data.type === "trip") {
      var durationFormatted = formatDuration(data.durationSeconds);
      var waitingFormatted = formatDuration(data.waitingSeconds);
      
      tripsSheet.appendRow([
        data.tripIdCode || "N/A",
        data.dateStr || timestampStr,
        data.driverName || "N/A",
        data.vehicleNumber || "N/A",
        data.vehicleCategory || "N/A",
        data.vehicleModel || "N/A",
        data.startTime || "N/A",
        data.endTime || "N/A",
        Number(data.distance || 0),
        durationFormatted,
        waitingFormatted,
        Number(data.totalFare || 0),
        Number(data.baseFare || 0),
        Number(data.perKmFare || 0),
        Number(data.waitingChargePerMin || 0),
        Number(data.minimumFare || 0),
        Number(data.nightChargePercent || 0),
        Number(data.ccCommission || 0),
        data.customerMobile || "",
        data.startLocation || "Unknown",
        data.endLocation || "Unknown",
        data.isPackageMeter ? "TRUE" : "FALSE",
        data.packageName || "",
        Number(data.packageBaseFare || 0),
        Number(data.includedKm || 0),
        Number(data.includedMinutes || 0),
        Number(data.extraKmRate || 0),
        Number(data.extraTimeRate || 0),
        Number(data.packageWaitingChargePerMin || 0),
        data.deviceId || ""
      ]);
      
      var lastRow = tripsSheet.getLastRow();
      if (data.isPackageMeter) {
        var rowRange = tripsSheet.getRange(lastRow, 1, 1, tripHeaders.length);
        rowRange.setBackground("#FFFDE7"); // Warm yellow highlight for package rentals
      }
      
      formatTableRows(tripsSheet, tripHeaders.length);

      return returnJson({
        status: "success",
        message: "Trip synced successfully"
      });

    // =========================================================================
    // 4. GET PROFILE / STATUS
    // =========================================================================
    } else if (data.type === "get_profile") {
      var totalDrivers = Math.max(0, driversSheet.getLastRow() - 1);
      return returnJson({
        status: "success",
        userCount: totalDrivers,
        message: "Profile query completed"
      });

    } else {
      return returnJson({
        status: "error",
        message: "Unknown request type: " + data.type
      });
    }

  } catch (error) {
    return returnJson({
      status: "error",
      message: error.toString()
    });
  }
}

// Helper to return standardized JSON response
function returnJson(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj))
    .setMimeType(ContentService.MimeType.JSON);
}

// Helper to extract digits from string
function extractDigits(str) {
  if (!str) return "";
  return String(str).replace(/\D/g, "");
}

// Helper to map headers to 1-based column indices
function getHeaderMap(sheet) {
  var lastCol = sheet.getLastColumn();
  if (lastCol === 0) return {};
  var headers = sheet.getRange(1, 1, 1, lastCol).getValues()[0];
  var map = {};
  for (var c = 0; c < headers.length; c++) {
    var h = String(headers[c]).trim();
    if (h) {
      map[h] = c + 1;
    }
  }
  return map;
}

// Helper to format duration in seconds into readable string
function formatDuration(seconds) {
  if (!seconds || isNaN(seconds)) return "0s";
  var hrs = Math.floor(seconds / 3600);
  var mins = Math.floor((seconds % 3600) / 60);
  var secs = seconds % 60;
  
  var parts = [];
  if (hrs > 0) parts.push(hrs + "h");
  if (mins > 0) parts.push(mins + "m");
  if (secs > 0 || parts.length === 0) parts.push(secs + "s");
  return parts.join(" ");
}

// Helper to open or create a sheet tab by name
function getOrCreateSheet(spreadsheet, name) {
  var sheet = spreadsheet.getSheetByName(name);
  if (!sheet) {
    sheet = spreadsheet.insertSheet(name);
  }
  return sheet;
}

// Helper to init headers and dynamically append any missing columns safely
function initHeadersWithUpgrade(sheet, expectedHeaders) {
  if (sheet.getLastRow() === 0) {
    sheet.appendRow(expectedHeaders);
  } else {
    // Check if expected headers exist in row 1; if missing, append to the right
    var lastCol = sheet.getLastColumn();
    var existingHeaders = sheet.getRange(1, 1, 1, lastCol).getValues()[0];
    for (var i = 0; i < expectedHeaders.length; i++) {
      var expH = expectedHeaders[i];
      var found = false;
      for (var j = 0; j < existingHeaders.length; j++) {
        if (String(existingHeaders[j]).trim().toLowerCase() === expH.toLowerCase()) {
          found = true;
          break;
        }
      }
      if (!found) {
        lastCol++;
        sheet.getRange(1, lastCol).setValue(expH);
        existingHeaders.push(expH);
      }
    }
  }
  
  // Style the header row with Trusty Yellow theme (#FBC02D)
  var totalCols = sheet.getLastColumn();
  if (totalCols > 0) {
    var headerRange = sheet.getRange(1, 1, 1, totalCols);
    headerRange.setFontWeight("bold");
    headerRange.setBackgroundColor("#FBC02D");
    headerRange.setFontColor("#111111");
    headerRange.setHorizontalAlignment("center");
    headerRange.setVerticalAlignment("middle");
    sheet.setRowHeight(1, 32);
    sheet.setFrozenRows(1);
  }
}

// Auto format table rows
function formatTableRows(sheet, colCount) {
  var lastRow = sheet.getLastRow();
  if (lastRow <= 1) return;
  for (var r = 2; r <= Math.min(lastRow, 50); r++) {
    sheet.setRowHeight(r, 24);
  }
  sheet.autoResizeColumns(1, colCount);
  var dataRange = sheet.getRange(2, 1, lastRow - 1, colCount);
  dataRange.setVerticalAlignment("middle");
}
