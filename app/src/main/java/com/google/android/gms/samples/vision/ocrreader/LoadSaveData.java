package com.google.android.gms.samples.vision.ocrreader;

import android.content.Context;
import android.util.Log;

import com.google.android.gms.samples.vision.ocrreader.model.CardInfo;

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
public class LoadSaveData {
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
    public static <T extends Serializable> void saveSerializable(Context context, T objectToSave, String fileName) {
        try {
            FileOutputStream fileOutputStream = context.openFileOutput(fileName, Context.MODE_PRIVATE);
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(fileOutputStream);

            objectOutputStream.writeObject(objectToSave);

            objectOutputStream.close();
            fileOutputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
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
        T objectToReturn = null;

        try {
            FileInputStream fileInputStream = context.openFileInput(fileName);
            ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream);
            objectToReturn = (T) objectInputStream.readObject();

            objectInputStream.close();
            fileInputStream.close();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }

        return objectToReturn;
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
