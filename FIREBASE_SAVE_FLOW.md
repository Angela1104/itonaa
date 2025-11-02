# Complete Firebase Save Flow

## Step-by-Step Saving Process

### Step 1: App Launch → Session Initialization
**When:** App starts (DashboardActivity.onCreate)

**Code Location:** `DashboardActivity.kt` lines 149-203

**What Happens:**
```kotlin
// Get or create session ID
val sessionId = SessionManager.getOrCreateSessionId(applicationContext)

// Check if session document exists
db.collection("devices").document(sessionId).get()
    .addOnSuccessListener { document ->
        if (!document.exists()) {
            // CREATE new session document
            db.collection("devices").document(sessionId).set(sessionData)
        } else {
            // UPDATE existing session
            db.collection("devices").document(sessionId).update("status", "active")
        }
    }
```

**Saves To:**
```
/devices/{session_id}
    ├── start_time: "2024-01-15T10:30:00Z"
    └── status: "active"
```

**Method:** `.set()` or `.update()`

---

### Step 2: Pin Location → Save Pinned Location
**When:** User pins location on map (HomeScreen - Save button clicked)

**Code Location:** `HomeScreen.kt` lines 175-210

**What Happens:**
```kotlin
val locationData = hashMapOf(
    "name" to name,
    "latitude" to loc.latitude,
    "longitude" to loc.longitude,
    "address" to address,
    "timestamp" to Timestamp.now(),
    "timestamp_iso" to "2024-01-15T10:30:00Z"
)

db.collection("devices").document(sId)
    .collection("pinned_locations")
    .add(locationData)  // Creates new document with auto-generated ID
    .addOnSuccessListener { documentReference ->
        // Navigate to AR Activity
    }
```

**Saves To:**
```
/devices/{session_id}/pinned_locations/{auto_generated_doc_id}
    ├── name: "Location Name"
    ├── latitude: 9.743900
    ├── longitude: 118.735700
    ├── address: "Geocoded address"
    ├── timestamp: Timestamp
    └── timestamp_iso: "2024-01-15T10:30:00Z"
```

**Method:** `.add()` (creates new document, Firebase auto-generates ID)

**Result:** Returns `documentReference` with auto-generated document ID

---

### Step 3: Set Centerpoint → Update Pinned Location
**When:** User double-taps on AR surface to set centerpoint

**Code Location:** `ARActivity.kt` lines 501-564

**What Happens:**
```kotlin
// Query to find most recent pinned location
db.collection("devices").document(sessionId)
    .collection("pinned_locations")
    .orderBy("timestamp", DESCENDING)
    .limit(1)
    .get()
    .addOnSuccessListener { snapshot ->
        if (!snapshot.isEmpty) {
            val pinnedLocationDoc = snapshot.documents[0]
            
            // UPDATE existing pinned location document
            pinnedLocationDoc.reference.update(centerpointData)
            
            // Store document ID for later use
            pinnedLocationDocId = pinnedLocationDoc.id
        } else {
            // CREATE new document (fallback if no pinned location)
            db.collection("devices").document(sessionId)
                .collection("pinned_locations")
                .add(combinedData)  // Creates new document
        }
    }
```

**Saves To:**
```
/devices/{session_id}/pinned_locations/{doc_id}  (UPDATES existing document)
    ├── [existing fields from Step 2]
    ├── ar_pose_x: 0.123          ← NEW
    ├── ar_pose_y: 0.456          ← NEW
    ├── ar_pose_z: 0.789          ← NEW
    ├── centerpoint_timestamp: Timestamp  ← NEW
    ├── centerpoint_timestamp_iso: "2024-01-15T10:35:00Z"  ← NEW
    ├── centerpoint_device_lat: 9.743900  ← NEW
    └── centerpoint_device_lon: 118.735700  ← NEW
```

**Method:** `.update()` (updates existing document) OR `.add()` (creates new if none exists)

**Stores:** `pinnedLocationDocId` in memory for later use

---

### Step 4: Detect Trunks → Real-time Saving
**When:** User clicks "Start Detection" and ML detects Rhizophora trunks inside boundary

**Code Location:** `ARActivity.kt` lines 336-437

**Trigger:** `LaunchedEffect(rawDetections, boundaryVisible, isDetecting)` (line 260)
- Runs automatically when new detections arrive
- Only processes if: `isDetecting && boundaryVisible && rawDetections.isNotEmpty()`

**What Happens (for EACH detected trunk):**
```kotlin
// 1. Filter: Only Rhizophora inside boundary
if (!isRhizo || !insideBoundary) {
    return@forEach  // Skip
}

// 2. Check duplicates
if (savedDetectionIds.contains(posKey)) {
    return@forEach  // Skip if already saved
}

// 3. Validate centerpoint connection
if (pinnedLocationDocId == null) {
    return@forEach  // Skip if no centerpoint
}

// 4. Prepare data
val data = hashMapOf(
    "session_id" to sessionId,
    "pinned_location_id" to pinnedLocationDocId!!,  // Connection to centerpoint
    "pinned_location" to pinnedLocationName,
    "inside_boundary" to true,
    "vector_position" to [x, y, z],
    "is_rhizophora" to 1,
    "is_alive" to 1 or 0,
    "dbh_cm" to dbhCm,
    "timestamp" to "2024-01-15T10:40:00Z",
    "timestamp_firestore" to Timestamp.now()
)

// 5. SAVE to Firebase
db.collection("trunk_detections")
    .document(trunkId)  // trunk_xyz123...
    .set(data)
    .addOnSuccessListener {
        savedDetectionIds.add(posKey)  // Mark as saved (prevent duplicates)
    }
```

**Saves To:**
```
/trunk_detections/{trunk_xyz123...}
    ├── session_id: "session_abc123..."
    ├── pinned_location_id: "auto_generated_doc_id"  ← Links to centerpoint
    ├── pinned_location: "Location Name"
    ├── inside_boundary: true
    ├── vector_position: [1.23, 4.56, 7.89]  (relative to centerpoint)
    ├── is_rhizophora: 1
    ├── is_alive: 1 or 0
    ├── dbh_cm: 15.5
    ├── timestamp: "2024-01-15T10:40:00Z"
    └── timestamp_firestore: Timestamp
```

**Method:** `.set()` (creates new document with custom ID)

**Frequency:** Real-time (every time a new detection passes filters)

---

## Complete Data Flow Timeline

```
1. App Launch
   └── CREATE: /devices/{session_id}
       └── Method: .set() or .update()

2. Pin Location (HomeScreen)
   └── CREATE: /devices/{session_id}/pinned_locations/{doc_id}
       └── Method: .add()  (auto-generated ID)

3. Set Centerpoint (ARActivity)
   └── UPDATE: /devices/{session_id}/pinned_locations/{doc_id}
       └── Method: .update()  (adds centerpoint fields)
       └── Stores: pinnedLocationDocId in memory

4. Start Detection → ML detects trunks
   └── FOR EACH detection (real-time):
       ├── Filter: Rhizophora + Inside boundary
       ├── Check: Not duplicate
       ├── Validate: pinnedLocationDocId exists
       └── CREATE: /trunk_detections/{trunk_id}
           └── Method: .set()  (custom ID)
           └── Includes: pinned_location_id (connection to centerpoint)
```

## Key Points

1. **Session Document**: Created once per app launch
2. **Pinned Location**: Created when user pins (`.add()`)
3. **Centerpoint**: Updates pinned location document (`.update()`)
4. **Trunk Detections**: Created in real-time, multiple times (`.set()`)
5. **Connection**: Trunk detections link to centerpoint via `pinned_location_id`
6. **Real-time Saving**: Detections save as they're detected (via LaunchedEffect)

## Firebase Methods Used

- `.set()` - Create or overwrite document
- `.add()` - Create document with auto-generated ID
- `.update()` - Update existing document fields
- `.get()` - Read/query documents

