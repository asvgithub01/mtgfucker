package io.asv.mtgocr.ocrreader;

import android.content.Context;
import android.util.Log;

import io.asv.mtgocr.ocrreader.model.CardInfo;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.List;

/**
 * Created by Alberto on 16/10/2016.
 */
public class DataUtils {
    public static void saveCardInfo(Context context, CardInfo cardInfo) {
        try {
            FileOutputStream fos = context.openFileOutput("cardInfoDb.json", Context.MODE_PRIVATE);
            ObjectOutputStream os = new ObjectOutputStream(fos);
            os.writeObject(cardInfo);
            os.close();
            fos.close();
        } catch (Exception e) {
            Log.e("Error", e.getMessage());
        }
    }

    public static List<CardInfo> loadCardInfo(Context context) {
        try {
            FileInputStream fis = context.openFileInput("cardInfoDb.json");
            ObjectInputStream is = new ObjectInputStream(fis);
            List<CardInfo> lstCardsInfo = (List<CardInfo>) is.readObject();
            is.close();
            fis.close();
            return lstCardsInfo;
        } catch (Exception e) {
            Log.e("Error", e.getMessage());
        }
        return null;
    }

    /*test*/
    public static synchronized <T extends Serializable> void saveSerializable(
            Context context, T objectToSave, String fileName) {
        File target = new File(context.getFilesDir(), fileName);
        File temporary = new File(context.getFilesDir(), fileName + ".tmp");
        File backup = new File(context.getFilesDir(), fileName + ".bak");
        try {
            FileOutputStream fileOutputStream = new FileOutputStream(temporary, false);
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(fileOutputStream);

            objectOutputStream.writeObject(objectToSave);

            objectOutputStream.close();
            fileOutputStream.close();
            if (backup.exists() && !backup.delete()) {
                throw new IOException("No se pudo borrar el backup antiguo de " + fileName);
            }
            if (target.exists() && !target.renameTo(backup)) {
                throw new IOException("No se pudo respaldar " + fileName);
            }
            if (!temporary.renameTo(target)) {
                if (backup.exists()) backup.renameTo(target);
                throw new IOException("No se pudo publicar " + fileName);
            }
            // Deliberately retain the previous known-good generation. A schema or serialization
            // regression must not be able to replace the user's only copy of the collection.
        } catch (Exception e) {
            if (temporary.exists() && !temporary.delete()) {
                Log.w("DataUtils", "No se pudo borrar el temporal de " + fileName);
            }
            Log.e("DataUtils", "No se pudo guardar " + fileName, e);
        }
    }

    /**
     * Loads a serializable object.
     *
     * @param context  The application context.
     * @param fileName The filename.
     * @param <T>      The object type.
     * @return the serializable object.
     */

    public static <T extends Serializable> T readSerializable(Context context, String fileName) {
        File target = new File(context.getFilesDir(), fileName);
        File backup = new File(context.getFilesDir(), fileName + ".bak");
        T value = readSerializableFile(target);
        if (value != null) return value;
        value = readSerializableFile(backup);
        if (value != null) {
            Log.e("DataUtils", "Se recuperó " + fileName + " desde el backup");
            return value;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static <T extends Serializable> T readSerializableFile(File file) {
        if (file == null || !file.isFile() || file.length() == 0L) return null;
        try (FileInputStream input = new FileInputStream(file);
             ObjectInputStream objects = new ObjectInputStream(input)) {
            return (T) objects.readObject();
        } catch (IOException | ClassNotFoundException | ClassCastException error) {
            Log.e("DataUtils", "No se pudo leer " + file.getAbsolutePath(), error);
            return null;
        }
    }

    /**
     * Removes a specified file.
     *
     * @param context  The application context.
     * @param filename The name of the file.
     */

    public static void removeSerializable(Context context, String filename) {
        context.deleteFile(filename);
    }

}
