package RonaldoWalker.subscriber

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

const val DB_NAME = "database.sql"
const val DB_VERSION = 1

class DatabaseHelper (context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION){

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE StudentLocation (
                ID INTEGER PRIMARY KEY AUTOINCREMENT,
                StudentID TEXT NOT NULL,
                Latitude REAL NOT NULL,
                Longitude REAL NOT NULL,
                Speed REAL NOT NULL
            )
            """.trimIndent()

        )
    }

    override fun onUpgrade(p0: SQLiteDatabase?, p1: Int, p2: Int) {
        // Content similar to enterprise DB
    }

    fun addLocationToDatabase(studentID: String, latitude: Double, longitude: Double, speed: Double) {
        val db = this.writableDatabase

        val cursor = db.rawQuery(
            "SELECT MIN(Speed), MAX(Speed) FROM StudentLocation WHERE StudentID = ?",
            arrayOf(studentID)
        )

        var minSpeed = speed
        var maxSpeed = speed

        if (cursor.moveToFirst()) {
            minSpeed = minOf(cursor.getDouble(0), speed)
            maxSpeed = maxOf(cursor.getDouble(1), speed)
        }
        cursor.close()

        val values = ContentValues().apply {
            put("StudentID", studentID)
            put("Latitude", latitude)
            put("Longitude", longitude)
            put("Speed", speed)
        }
        db.insert("StudentLocation", null, values)
        db.close()
    }


}