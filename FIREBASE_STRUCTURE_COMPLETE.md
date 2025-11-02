# Complete Firebase Firestore Structure

## Full Database Hierarchy

```
Firebase Firestore
│
├── devices (collection)
│   └── {session_id} (document) - e.g., "session_abc123def456..."
│       ├── start_time: "2024-01-15T10:30:00Z" (string, ISO 8601)
│       ├── status: "active" | "background" | "inactive" (string)
│       │
│       └── pinned_locations (subcollection)
│           └── {auto_generated_doc_id} (document) - e.g., "abc123xyz789"
│               ├── name: "Location Name" (string)
│               ├── latitude: 9.743900 (double)
│               ├── longitude: 118.735700 (double)
│               ├── address: "Reverse geocoded address or coordinates" (string)
│               ├── timestamp: Timestamp (Firestore Timestamp - for querying)
│               ├── timestamp_iso: "2024-01-15T10:30:00Z" (string - for readability)
│               │
│               ├── [Centerpoint fields - added when centerpoint is set]
│               ├── ar_pose_x: 0.123 (double) - AR world X coordinate
│               ├── ar_pose_y: 0.456 (double) - AR world Y coordinate
│               ├── ar_pose_z: 0.789 (double) - AR world Z coordinate
│               ├── centerpoint_timestamp: Timestamp (Firestore Timestamp)
│               ├── centerpoint_timestamp_iso: "2024-01-15T10:35:00Z" (string)
│               ├── centerpoint_device_lat: 9.743900 (double) - GPS when centerpoint set
│               └── centerpoint_device_lon: 118.735700 (double) - GPS when centerpoint set
│
└── trunk_detections (collection - separate, not nested under devices)
    └── trunk_xyz12345... (document) - e.g., "trunk_a1b2c3d4"
        ├── session_id: "session_abc123def456..." (string) - Links to devices/{session_id}
        ├── pinned_location_id: "abc123xyz789" (string) - Links to pinned_locations/{doc_id}
        ├── pinned_location: "Location Name" (string) - For readability
        ├── inside_boundary: true (boolean) - Always true (filtered before saving)
        ├── vector_position: [1.23, 4.56, 7.89] (array of doubles) - Relative to centerpoint (0,0,0)
        ├── is_rhizophora: 1 (int) - 1 = yes, 0 = no (always 1, filtered before saving)
        ├── is_alive: 1 | 0 (int) - 1 = alive, 0 = dead
        ├── dbh_cm: 15.5 (double) - Diameter at Breast Height in centimeters (0-500)
        ├── timestamp: "2024-01-15T10:40:00Z" (string, ISO 8601)
        └── timestamp_firestore: Timestamp (Firestore Timestamp - for querying)
```

## Complete Path Examples

### 1. Device Session Document
```
/devices/session_abc123def456
    ├── start_time: "2024-01-15T10:30:00Z"
    └── status: "active"
```

### 2. Pinned Location Document (Before Centerpoint)
```
/devices/session_abc123def456/pinned_locations/abc123xyz789
    ├── name: "Mangrove Area 1"
    ├── latitude: 9.743900
    ├── longitude: 118.735700
    ├── address: "Coastal Road, Palawan, Philippines"
    ├── timestamp: Timestamp(2024-01-15 10:30:00)
    └── timestamp_iso: "2024-01-15T10:30:00Z"
```

### 3. Pinned Location Document (After Centerpoint Added)
```
/devices/session_abc123def456/pinned_locations/abc123xyz789
    ├── name: "Mangrove Area 1"
    ├── latitude: 9.743900
    ├── longitude: 118.735700
    ├── address: "Coastal Road, Palawan, Philippines"
    ├── timestamp: Timestamp(2024-01-15 10:30:00)
    ├── timestamp_iso: "2024-01-15T10:30:00Z"
    │
    ├── ar_pose_x: 0.123
    ├── ar_pose_y: 0.456
    ├── ar_pose_z: 0.789
    ├── centerpoint_timestamp: Timestamp(2024-01-15 10:35:00)
    ├── centerpoint_timestamp_iso: "2024-01-15T10:35:00Z"
    ├── centerpoint_device_lat: 9.743900
    └── centerpoint_device_lon: 118.735700
```

### 4. Trunk Detection Document
```
/trunk_detections/trunk_a1b2c3d4
    ├── session_id: "session_abc123def456"
    ├── pinned_location_id: "abc123xyz789"
    ├── pinned_location: "Mangrove Area 1"
    ├── inside_boundary: true
    ├── vector_position: [1.23, 4.56, 7.89]
    ├── is_rhizophora: 1
    ├── is_alive: 1
    ├── dbh_cm: 15.5
    ├── timestamp: "2024-01-15T10:40:00Z"
    └── timestamp_firestore: Timestamp(2024-01-15 10:40:00)
```

## Data Relationships

```
┌─────────────────────────────────────┐
│ devices/{session_id}                │
│   └── session_id                    │
│       ├── start_time                │
│       └── status                    │
└───────────┬─────────────────────────┘
            │
            │ 1 session can have
            │ multiple pinned locations
            ↓
┌─────────────────────────────────────┐
│ devices/{session_id}/pinned_        │
│   locations/{doc_id}                │
│   └── pinned_location_id            │
│       ├── name                      │
│       ├── GPS (lat, lon)            │
│       └── centerpoint (AR pose)     │
└───────────┬─────────────────────────┘
            │
            │ 1 centerpoint can have
            │ multiple trunk detections
            ↓
┌─────────────────────────────────────┐
│ trunk_detections/{trunk_id}         │
│   ├── session_id (links to device)  │
│   └── pinned_location_id (links to  │
│       centerpoint)                  │
│       └── vector_position (relative │
│           to centerpoint 0,0,0)     │
└─────────────────────────────────────┘
```

## Connection Keys

### Primary Keys
- **Session ID**: `session_id` (UUID format: `session_abc123...`)
  - Stored in: `SessionManager` (SharedPreferences)
  - Links: `devices/{session_id}` ↔ `trunk_detections/{trunk_id}`

### Foreign Keys
- **Pinned Location ID**: `pinned_location_id`
  - Auto-generated by Firestore when using `.add()`
  - Links: `pinned_locations/{doc_id}` ↔ `trunk_detections/{trunk_id}`
  - Used to verify which centerpoint was used for boundary check

## Query Examples

### Get all trunks for a specific centerpoint
```javascript
db.collection("trunk_detections")
  .where("pinned_location_id", "==", "abc123xyz789")
  .get()
```

### Get all trunks for a session
```javascript
db.collection("trunk_detections")
  .where("session_id", "==", "session_abc123def456")
  .get()
```

### Get centerpoint for a trunk detection
```javascript
// Step 1: Get trunk detection
const trunk = await db.collection("trunk_detections")
  .doc("trunk_a1b2c3d4")
  .get()

// Step 2: Get centerpoint using pinned_location_id
const centerpoint = await db.collection("devices")
  .doc(trunk.data().session_id)
  .collection("pinned_locations")
  .doc(trunk.data().pinned_location_id)
  .get()
```

### Get all centerpoints with their trunk counts
```javascript
// Get all pinned locations with centerpoints
const locations = await db.collection("devices")
  .doc(sessionId)
  .collection("pinned_locations")
  .where("ar_pose_x", "!=", null)  // Has centerpoint
  .get()

// For each location, count trunks
for (const loc of locations.docs) {
  const trunkCount = await db.collection("trunk_detections")
    .where("pinned_location_id", "==", loc.id)
    .count()
    .get()
}
```

## Field Types Summary

| Field | Type | Description |
|-------|------|-------------|
| `session_id` | string | Unique device session identifier |
| `start_time` | string | ISO 8601 timestamp |
| `status` | string | "active", "background", "inactive" |
| `name` | string | User-entered location name |
| `latitude`, `longitude` | double | GPS coordinates |
| `ar_pose_x`, `ar_pose_y`, `ar_pose_z` | double | AR world coordinates |
| `vector_position` | array | [x, y, z] relative to centerpoint |
| `is_rhizophora` | int | 1 = yes, 0 = no |
| `is_alive` | int | 1 = alive, 0 = dead |
| `dbh_cm` | double | Diameter in centimeters (0-500) |
| `timestamp` | string | ISO 8601 format |
| `timestamp_firestore` | Timestamp | Firestore native timestamp |
| `pinned_location_id` | string | Foreign key to centerpoint document |

