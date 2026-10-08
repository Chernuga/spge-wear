package com.chernuga.spge.mobile;

import android.content.Context;
import android.util.Log;

import com.chernuga.spge.shared.Lesson;
import com.chernuga.spge.shared.SyncContract;
import com.google.android.gms.wearable.MessageClient;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Publishes the schedule to the watch over the Wearable <b>message</b> channel.
 *
 * <p>Originally this used {@code DataClient}. On the target Galaxy Watch6 the
 * Data Layer accepted the writes on the phone side (the transport counters
 * showed {@code DataItem SET (6)}) but the watch's data model recorded
 * {@code DataItem SET (0)} and no listener was ever invoked — and inspection of
 * the watch's registered listeners showed every first-party companion service
 * registered for {@code MESSAGE_RECEIVED} and none for {@code DATA_CHANGED}.
 * Messages are therefore the transport that actually works here.
 *
 * <p>The trade-off is durability: a message is delivered only to a currently
 * connected node, whereas a DataItem is replicated and replayed. To keep the
 * offline behaviour the timetable needs, the receiver persists every snapshot
 * it accepts to disk, so the watch still renders after a restart or when the
 * phone is absent.
 */
public final class SyncSender {

    private static final String TAG = "SpgeSyncSender";

    /**
     * Payload budget per message.
     *
     * <p>The message channel is limited to roughly 100 KB, and the limit covers
     * the whole serialised asset, so chunks stay comfortably below it.
     */
    private static final int CHUNK_BYTES = 60 * 1024;

    private SyncSender() {}

    public interface Callback {
        void onSent(int nodeCount);
        void onNoNodes();
        void onError(String message);
    }

    public static void send(Context context, List<Lesson> lessons, Callback cb) {
        final MessageClient messageClient = Wearable.getMessageClient(context);

        List<String> chunks;
        try {
            chunks = chunk(lessons);
        } catch (JSONException e) {
            cb.onError("encode failed: " + e.getMessage());
            return;
        }

        final long generation = System.currentTimeMillis();

        Wearable.getNodeClient(context).getConnectedNodes()
                .addOnSuccessListener(nodes -> {
                    if (nodes.isEmpty()) {
                        cb.onNoNodes();
                        return;
                    }
                    Log.d(TAG, "sending " + lessons.size() + " lessons in "
                            + chunks.size() + " message(s) to " + nodes.size() + " node(s)");
                    sendToNodes(context, messageClient, nodes, chunks, generation,
                            lessons.size(), cb);
                })
                .addOnFailureListener(e -> cb.onError(
                        e.getMessage() == null ? e.toString() : e.getMessage()));
    }

    private static void sendToNodes(Context context,
                                    MessageClient messageClient,
                                    List<Node> nodes,
                                    List<String> chunks,
                                    long generation,
                                    int lessonCount,
                                    Callback cb) {
        // One counter across all nodes and chunks, so the callback fires only
        // after the entire fan-out has completed.
        final int[] remaining = {nodes.size() * chunks.size()};
        final boolean[] failed = {false};

        for (Node node : nodes) {
            for (int i = 0; i < chunks.size(); i++) {
                byte[] payload;
                try {
                    payload = envelope(chunks.get(i), i, chunks.size(),
                            generation, lessonCount).getBytes(StandardCharsets.UTF_8);
                } catch (JSONException e) {
                    if (!failed[0]) {
                        failed[0] = true;
                        cb.onError("encode failed: " + e.getMessage());
                    }
                    return;
                }

                final String path = SyncContract.PATH_SCHEDULE + "/" + i;
                messageClient.sendMessage(node.getId(), path, payload)
                        .addOnSuccessListener(id -> {
                            Log.d(TAG, "chunk sent to " + node.getDisplayName());
                            if (--remaining[0] == 0 && !failed[0]) {
                                cb.onSent(nodes.size());
                            }
                        })
                        .addOnFailureListener(e -> {
                            Log.w(TAG, "chunk failed", e);
                            if (!failed[0]) {
                                failed[0] = true;
                                cb.onError(e.getMessage() == null
                                        ? e.toString() : e.getMessage());
                            }
                        });
            }
        }
    }

    /**
     * Wraps a chunk with the metadata the receiver needs to reassemble a
     * snapshot and detect a stale or partial send.
     */
    private static String envelope(String lessonsJson, int index, int total,
                                   long generation, int lessonCount)
            throws JSONException {
        JSONObject o = new JSONObject();
        o.put(SyncContract.KEY_LESSONS, lessonsJson);
        o.put(SyncContract.KEY_CHUNK_INDEX, index);
        o.put(SyncContract.KEY_CHUNK_TOTAL, total);
        o.put(SyncContract.KEY_GENERATION, generation);
        o.put(SyncContract.KEY_TOTAL_LESSONS, lessonCount);
        return o.toString();
    }

    /** Splits lessons into JSON arrays that each fit the per-message budget. */
    private static List<String> chunk(List<Lesson> lessons) throws JSONException {
        List<String> out = new ArrayList<>();
        JSONArray current = new JSONArray();
        int currentBytes = 0;

        for (Lesson l : lessons) {
            String encoded = l.toJson().toString();
            int size = encoded.getBytes(StandardCharsets.UTF_8).length + 1;

            if (current.length() > 0 && currentBytes + size > CHUNK_BYTES) {
                out.add(current.toString());
                current = new JSONArray();
                currentBytes = 0;
            }
            current.put(l.toJson());
            currentBytes += size;
        }

        if (current.length() > 0) out.add(current.toString());
        if (out.isEmpty()) out.add("[]");
        return out;
    }
}
