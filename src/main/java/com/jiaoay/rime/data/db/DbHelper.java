package com.jiaoay.rime.data.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.NonNull;

public class DbHelper extends SQLiteOpenHelper {

    public static final String CREATE_STUDENT =
            "create table if not exists  t_data ("
                    + "id integer primary key, text TEXT, html TEXT, type integer, time integer)";

    public DbHelper(Context context, String name) {
        super(context, name, null, 3);
    }

    @Override
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
    }

    @Override
    public void onCreate(@NonNull SQLiteDatabase db) {
        db.execSQL(CREATE_STUDENT);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

        if (oldVersion == 1) {
            db.execSQL("ALTER TABLE t_clipboard RENAME TO t_data");
        } else {
            db.execSQL(CREATE_STUDENT);
        }
    }
}
