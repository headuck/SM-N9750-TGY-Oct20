package com.android.server.wifi.iwc;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonSerializer;
import com.google.gson.JsonSyntaxException;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;

public class IWCJsonFile<T> extends IWCFile {
    private final Class<T> clazz;
    private Gson mGson;

    @Override // com.android.server.wifi.iwc.IWCFile
    public /* bridge */ /* synthetic */ BufferedReader getBufferedReader() throws IOException {
        return super.getBufferedReader();
    }

    @Override // com.android.server.wifi.iwc.IWCFile
    public /* bridge */ /* synthetic */ long getSize() {
        return super.getSize();
    }

    @Override // com.android.server.wifi.iwc.IWCFile
    public /* bridge */ /* synthetic */ boolean isFileExists() {
        return super.isFileExists();
    }

    @Override // com.android.server.wifi.iwc.IWCFile
    public /* bridge */ /* synthetic */ String readFile() throws IOException {
        return super.readFile();
    }

    @Override // com.android.server.wifi.iwc.IWCFile
    public /* bridge */ /* synthetic */ void writeData(String str) {
        super.writeData(str);
    }

    @Override // com.android.server.wifi.iwc.IWCFile
    public /* bridge */ /* synthetic */ void writeDataAppend(String str) {
        super.writeDataAppend(str);
    }

    public IWCJsonFile(String filePath, Class<T> clazz2, JsonSerializer<T> serializer, JsonDeserializer<T> deserializer, boolean compact) {
        super(filePath);
        GsonBuilder gsonBuilder = new GsonBuilder();
        if (serializer != null) {
            gsonBuilder.registerTypeAdapter(clazz2, serializer);
        }
        if (deserializer != null) {
            gsonBuilder.registerTypeAdapter(clazz2, deserializer);
        }
        if (!compact) {
            gsonBuilder.setPrettyPrinting();
        }
        this.mGson = gsonBuilder.create();
        this.clazz = clazz2;
    }

    public void save(T jsonObject) {
        writeData(this.mGson.toJson((Object) jsonObject));
    }

    public T load() throws IOException, FileNotFoundException, JsonSyntaxException {
        BufferedReader br = null;
        try {
            br = getBufferedReader();
            T t = (T) this.mGson.fromJson((Reader) br, (Class) this.clazz);
            if (br != null) {
                br.close();
            }
            return t;
        } catch (Exception e) {
            throw e;
        } catch (Throwable th) {
            if (br != null) {
                br.close();
            }
            throw th;
        }
    }
}
