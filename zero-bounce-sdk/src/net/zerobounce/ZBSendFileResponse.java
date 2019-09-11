package net.zerobounce;

import com.google.gson.*;
import com.google.gson.annotations.SerializedName;

import java.lang.reflect.Type;

public class ZBSendFileResponse {
    @SerializedName("success") Boolean success;
    @SerializedName("message") String message = null;
    @SerializedName("file_name") String fileName = null;
    @SerializedName("file_id") String fileId = null;

    @Override
    public String toString() {
        return "ZBSendFileResponse{" +
                "success=" + success +
                ", message='" + message + '\'' +
                ", fileName='" + fileName + '\'' +
                ", fileId='" + fileId + '\'' +
                '}';
    }

    public static class Deserializer implements JsonDeserializer<ZBSendFileResponse> {

        @Override
        public ZBSendFileResponse deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {

            JsonObject jobject = jsonElement.getAsJsonObject();
            ZBSendFileResponse r = new ZBSendFileResponse();
            if(jobject.has("success")) r.success = jobject.get("success").getAsBoolean();
            if(jobject.has("message")) {
                if (jobject.get("message").isJsonArray()) {
                    JsonArray array = jobject.get("message").getAsJsonArray();
                    if (array.size() > 0) r.message = array.get(0).getAsString();
                } else {
                    r.message = jobject.get("message").getAsString();
                }
            }
            if(jobject.has("file_name")) r.fileName = jobject.get("file_name").getAsString();
            if(jobject.has("file_id")) r.fileId = jobject.get("file_id").getAsString();

            return r;
        }
    }
}
