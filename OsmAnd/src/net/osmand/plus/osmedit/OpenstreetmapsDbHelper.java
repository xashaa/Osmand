package net.osmand.plus.osmedit;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import net.osmand.osm.edit.Entity;
import net.osmand.osm.edit.Node;
import net.osmand.osm.edit.Way;
import net.osmand.util.Algorithms;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import gnu.trove.list.array.TLongArrayList;

public class OpenstreetmapsDbHelper extends SQLiteOpenHelper {

	private static final int DATABASE_VERSION = 6;
	public static final String OPENSTREETMAP_DB_NAME = "openstreetmap"; //$NON-NLS-1$
	private static final String OPENSTREETMAP_TABLE_NAME = "openstreetmaptable"; //$NON-NLS-1$
	private static final String OPENSTREETMAP_COL_ID = "id"; //$NON-NLS-1$
	private static final String OPENSTREETMAP_COL_LAT= "lat"; //$NON-NLS-1$
	private static final String OPENSTREETMAP_COL_LON= "lon"; //$NON-NLS-1$
	private static final String OPENSTREETMAP_COL_TAGS = "tags"; //$NON-NLS-1$
	private static final String OPENSTREETMAP_COL_ACTION = "action"; //$NON-NLS-1$
	private static final String OPENSTREETMAP_COL_COMMENT = "comment"; //$NON-NLS-1$
	private static final String OPENSTREETMAP_COL_CHANGED_TAGS = "changed_tags";
	private static final String OPENSTREETMAP_COL_ENTITY_TYPE = "entity_type";

	private static final String WAY_NODES_IDS_TABLE_NAME = "way_nodes_ids_table";
	private static final String WAY_COL_ID = "id";
	private static final String NODES_COL_IDS = "node_id";

	private static final String WAY_NODES_IDS_TABLE_CREATE = "CREATE TABLE IF NOT EXISTS " +
			WAY_NODES_IDS_TABLE_NAME + " (" +
			WAY_COL_ID + " int , " +
			NODES_COL_IDS + " int);";

	private static final String OPENSTREETMAP_TABLE_CREATE = "CREATE TABLE " + OPENSTREETMAP_TABLE_NAME + " (" + //$NON-NLS-1$ //$NON-NLS-2$
			OPENSTREETMAP_COL_ID + " bigint,"+
			OPENSTREETMAP_COL_LAT + " double," + OPENSTREETMAP_COL_LON + " double," +
			OPENSTREETMAP_COL_TAGS + " VARCHAR(2048)," +
			OPENSTREETMAP_COL_ACTION + " TEXT, " + OPENSTREETMAP_COL_COMMENT + " TEXT," +
			" " + OPENSTREETMAP_COL_CHANGED_TAGS + " TEXT, " + OPENSTREETMAP_COL_ENTITY_TYPE + " TEXT);";
	List<OpenstreetmapPoint> cache = null; 	

	public OpenstreetmapsDbHelper(Context context) {
		super(context, OPENSTREETMAP_DB_NAME, null, DATABASE_VERSION);
	}
	
	@Override
	public void onCreate(SQLiteDatabase db) {
		db.execSQL(OPENSTREETMAP_TABLE_CREATE);
		db.execSQL(WAY_NODES_IDS_TABLE_CREATE);
	}
	
	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		if(newVersion == 4) {
			db.execSQL("DROP TABLE IF EXISTS " + OPENSTREETMAP_TABLE_NAME);
			db.execSQL(OPENSTREETMAP_TABLE_CREATE);	
		}
		if (oldVersion < 5) {
			db.execSQL("ALTER TABLE " + OPENSTREETMAP_TABLE_NAME + " ADD " + OPENSTREETMAP_COL_CHANGED_TAGS + " TEXT");
		}
		if (oldVersion < 6) {
			db.execSQL("ALTER TABLE " + OPENSTREETMAP_TABLE_NAME + " ADD " + OPENSTREETMAP_COL_ENTITY_TYPE + " TEXT");
			db.execSQL("UPDATE " + OPENSTREETMAP_TABLE_NAME +
					" SET " + OPENSTREETMAP_COL_ENTITY_TYPE + " = ? " +
					"WHERE " + OPENSTREETMAP_COL_ENTITY_TYPE + " IS NULL", new String[]{Entity.EntityType.NODE.toString()});
			db.execSQL(WAY_NODES_IDS_TABLE_CREATE);
		}
	}

	public List<OpenstreetmapPoint> getOpenstreetmapPoints() {
		if(cache == null ) {
			return checkOpenstreetmapPoints();
		}
		return cache;
	}
	
	public boolean addOpenstreetmap(OpenstreetmapPoint p) {
		SQLiteDatabase db = getWritableDatabase();
		if (db != null) {
			StringBuilder tags = new StringBuilder();
			Entity entity = p.getEntity();
			Iterator<Entry<String, String>> eit = entity.getTags().entrySet().iterator();
			while(eit.hasNext()) {
				Entry<String, String> e = eit.next();
				if(Algorithms.isEmpty(e.getKey()) || Algorithms.isEmpty(e.getValue())) {
					continue;
				}
				tags.append(e.getKey()).append("$$$").append(e.getValue());
				if(eit.hasNext()) {
					tags.append("$$$");
				}
			}
			Set<String> chTags = p.getEntity().getChangedTags();
			StringBuilder changedTags = new StringBuilder();
			if (chTags != null) {
				Iterator<String> iterator = chTags.iterator();
				while (iterator.hasNext()) {
					changedTags.append(iterator.next());
					if (iterator.hasNext()) {
						changedTags.append("$$$");
					}
				}
			}
			db.execSQL("DELETE FROM " + OPENSTREETMAP_TABLE_NAME +
					" WHERE " + OPENSTREETMAP_COL_ID + " = ?", new Object[]{p.getId()});
			db.execSQL("INSERT INTO " + OPENSTREETMAP_TABLE_NAME +
							" (" + OPENSTREETMAP_COL_ID + ", " +
							OPENSTREETMAP_COL_LAT + ", " +
							OPENSTREETMAP_COL_LON + ", " +
							OPENSTREETMAP_COL_TAGS + ", " +
							OPENSTREETMAP_COL_ACTION + ", " +
							OPENSTREETMAP_COL_COMMENT + ", " +
							OPENSTREETMAP_COL_CHANGED_TAGS + ", " +
							OPENSTREETMAP_COL_ENTITY_TYPE + ")" +
							" VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
					new Object[]{p.getId(), p.getLatitude(), p.getLongitude(), tags.toString(),
							OsmPoint.stringAction.get(p.getAction()), p.getComment(),
							chTags == null ? null : changedTags.toString(), Entity.EntityType.valueOf(entity)});
			
			if (Entity.EntityType.valueOf(entity) == Entity.EntityType.WAY) {
				addNodeIdsForWay((Way) entity);
			}
			db.close();
			checkOpenstreetmapPoints();
			return true;
		}
		return false;
	}
	
	
	
	public boolean deletePOI(OpenstreetmapPoint p) {
		SQLiteDatabase db = getWritableDatabase();
		if (db != null) {
			db.execSQL("DELETE FROM " + OPENSTREETMAP_TABLE_NAME +
					" WHERE " + OPENSTREETMAP_COL_ID + " = ?", new Object[] { p.getId() }); //$NON-NLS-1$ //$NON-NLS-2$
			Entity entity = p.getEntity();
			if (Entity.EntityType.valueOf(entity) == Entity.EntityType.WAY) {
				db.execSQL("DELETE FROM " + WAY_NODES_IDS_TABLE_NAME +
						" WHERE " + WAY_COL_ID + " = ?", new Object[]{entity.getId()});
			}
			db.close();
			checkOpenstreetmapPoints();
			return true;
		}
		return false;
	}
	

	private List<OpenstreetmapPoint> checkOpenstreetmapPoints(){
		SQLiteDatabase db = getReadableDatabase();
		List<OpenstreetmapPoint> points = new ArrayList<OpenstreetmapPoint>();
		if (db != null) {
			Cursor query = db.rawQuery("SELECT " +
					OPENSTREETMAP_COL_ID + ", " +
					OPENSTREETMAP_COL_LAT + "," +
					OPENSTREETMAP_COL_LON + "," +
					OPENSTREETMAP_COL_ACTION + "," +
					OPENSTREETMAP_COL_COMMENT + "," +
					OPENSTREETMAP_COL_TAGS + "," +
					OPENSTREETMAP_COL_CHANGED_TAGS + "," +
					OPENSTREETMAP_COL_ENTITY_TYPE +
					" FROM " + OPENSTREETMAP_TABLE_NAME, null);
			if (query.moveToFirst()) {
				do {
					OpenstreetmapPoint p = new OpenstreetmapPoint();
					String entityType = query.getString(7);
					Entity entity = null;
					if (entityType != null && Entity.EntityType.valueOf(entityType) == Entity.EntityType.NODE) {
						entity = new Node(query.getDouble(1),
								query.getDouble(2),
								query.getLong(0));
					} else if (entityType != null && Entity.EntityType.valueOf(entityType) == Entity.EntityType.WAY) {
						entity = new Way(query.getLong(0), getNodeIdsForWayId(query.getLong(0)),
								query.getDouble(1),
								query.getDouble(2));
					}
					if (entity != null) {
						String tags = query.getString(5);
						String[] split = tags.split("\\$\\$\\$");
						for (int i = 0; i < split.length - 1; i += 2) {
							entity.putTagNoLC(split[i].trim(), split[i + 1].trim());
						}
						String changedTags = query.getString(6);
						if (changedTags != null) {
							entity.setChangedTags(new HashSet<>(Arrays.asList(changedTags.split("\\$\\$\\$"))));
						}
						p.setEntity(entity);
						p.setAction(query.getString(3));
						p.setComment(query.getString(4));
						points.add(p);
					}

				} while (query.moveToNext());
			}
			query.close();
		}
		cache = points;
		return points;
	}


	private void addNodeIdsForWay(Way way) {
		SQLiteDatabase db = getWritableDatabase();
		if (db != null) {
			try {
				ContentValues values = new ContentValues();
				db.execSQL("DELETE FROM " + WAY_NODES_IDS_TABLE_NAME +
						" WHERE " + WAY_COL_ID + " = ?", new Object[]{way.getId()});

				db.beginTransaction();
				for (Long nodeId : way.getNodeIds().toArray()) {
					if (nodeId != 0) {
						values.put(WAY_COL_ID, way.getId());
						values.put(NODES_COL_IDS, nodeId);
						db.insert(WAY_NODES_IDS_TABLE_NAME, null, values);
					}
				}
				db.setTransactionSuccessful();
			} finally {
				db.endTransaction();
				db.close();
			}
		}
	}

	private TLongArrayList getNodeIdsForWayId(long id) {
		TLongArrayList nodeIds = new TLongArrayList();
		SQLiteDatabase db = getWritableDatabase();
		if (db != null) {
			Cursor query = db.rawQuery("SELECT " + NODES_COL_IDS + " FROM " +
							WAY_NODES_IDS_TABLE_NAME + " WHERE " + OPENSTREETMAP_COL_ID + " = ?",
					new String[]{String.valueOf(id)});
			if (query.moveToFirst()) {
				do {
					nodeIds.add(query.getLong(0));

				} while (query.moveToNext());
			}
			query.close();
		}
		return nodeIds;
	}

	public long getMinID() {
		SQLiteDatabase db = getReadableDatabase();
		long minID = 0;
		if (db != null) {
			Cursor query = db.rawQuery("SELECT MIN(" + OPENSTREETMAP_COL_ID + ") FROM " + OPENSTREETMAP_TABLE_NAME, null); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			if (query.moveToFirst()) {
				minID = query.getLong(0);
			}
			query.close();
		}
		return minID;
	}

}