# Firebase Firestore Structure

## Complete Database Structure

```
Firebase Firestore
└── devices (collection)
    └── {session_id} (document) - e.g., "session_abc123..."
        ├── start_time: "2024-01-15T10:30:00Z" (string)
        ├── status: "active" | "background" | "inactive" (string)
        │
        └── pinned_locations (subcollection)
            └── {auto_generated_doc_id} (document)
                ├── name: "User's Location Name" (string)
                ├── latitude: 9.743900 (double)
                ├── longitude: 118.735700 (double)
                ├── address: "Geocoded address" (string)
                ├── timestamp: Timestamp (Firestore Timestamp)
                ├── timestamp_iso: "2024-01-15T10:30:00Z" (string)
                │
                ├── [Centerpoint fields - added when centerpoint is set]
                ├── ar_pose_x: 0.123 (double)
                ├── ar_pose_y: 0.456 (double)
                ├── ar_pose_z: 0.789 (double)
                ├── centerpoint_timestamp: Timestamp
                ├── centerpoint_timestamp_iso: "2024-01-15T10:35:00Z" (string)
                ├── centerpoint_device_lat: 9.743900 (double)
                └── centerpoint_device_lon: 118.735700 (double)

└── trunk_detections (collection - separate, not nested)
    └── trunk_xyz123... (document)
        ├── session_id: "session_abc123..." (string)
        ├── pinned_location: "Location Name" (string)
        ├── inside_boundary: true (boolean)
        ├── vector_position: [x, y, z] (array of doubles)
        ├── is_rhizophora: 1 (int)
        ├── is_alive: 1 | 0 (int)
        ├── dbh_cm: 15.5 (double)
        ├── timestamp: "2024-01-15T10:40:00Z" (string)
        └── timestamp_firestore: Timestamp
```

## Data Flow

### Step 1: App Launch (DashboardActivity)
- Creates/updates: `/devices/{session_id}` with `start_time` and `status`

### Step 2: Pin Location (HomeScreen)
- Creates: `/devices/{session_id}/pinned_locations/{doc_id}`
- Stores: name, GPS coordinates, address, timestamps

### Step 3: Set Centerpoint (ARActivity)
- Updates: Same document from Step 2
- Adds: AR pose coordinates, centerpoint timestamps, GPS at centerpoint time

### Step 4: Save Detections (ARActivity)
- Creates: `/trunk_detections/{trunk_id}`
- Stores: Session ID, location name, detection data

