package de.appplant.cordova.plugin.notification;

import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import de.appplant.cordova.plugin.notification.Notification.Type;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.json.JSONException;
import org.json.JSONObject;

public class Manager {
    private Context context;

    private Manager(Context context) {
        this.context = context;
    }

    public static Manager getInstance(Context context) {
        return new Manager(context);
    }

    public Notification schedule(JSONObject options, Class<?> receiver) {
        return schedule(new Options(this.context).parse(options), (Class) receiver);
    }

    public Notification schedule(Options options, Class<?> receiver) {
        Notification notification = new Builder(options).setTriggerReceiver(receiver).build();
        notification.schedule();
        return notification;
    }

    public Notification update(int id, JSONObject updates, Class<?> receiver) {
        Notification notification = get(id);
        if (notification == null) {
            return null;
        }
        notification.cancel();
        JSONObject options = mergeJSONObjects(notification.getOptions().getDict(), updates);
        try {
            options.putOpt("updatedAt", Long.valueOf(new Date().getTime()));
        } catch (JSONException e) {
        }
        return schedule(options, (Class) receiver);
    }

    public Notification clear(int id) {
        Notification notification = get(id);
        if (notification != null) {
            notification.clear();
        }
        return notification;
    }

    public Notification cancel(int id) {
        Notification notification = get(id);
        if (notification != null) {
            notification.cancel();
        }
        return notification;
    }

    public void clearAll() {
        for (Notification notification : getAll()) {
            notification.clear();
        }
        getNotMgr().cancelAll();
    }

    public void cancelAll() {
        for (Notification notification : getAll()) {
            notification.cancel();
        }
        getNotMgr().cancelAll();
    }

    public List<Integer> getIds() {
        Set<String> keys = getPrefs().getAll().keySet();
        ArrayList<Integer> ids = new ArrayList();
        for (String key : keys) {
            ids.add(Integer.valueOf(Integer.parseInt(key)));
        }
        return ids;
    }

    public List<Integer> getIdsByType(Type type) {
        List<Notification> notifications = getAll();
        ArrayList<Integer> ids = new ArrayList();
        for (Notification notification : notifications) {
            if (notification.getType() == type) {
                ids.add(Integer.valueOf(notification.getId()));
            }
        }
        return ids;
    }

    public List<Notification> getByIds(List<Integer> ids) {
        ArrayList<Notification> notifications = new ArrayList();
        for (Integer intValue : ids) {
            Notification notification = get(intValue.intValue());
            if (notification != null) {
                notifications.add(notification);
            }
        }
        return notifications;
    }

    public List<Notification> getAll() {
        return getByIds(getIds());
    }

    public List<Notification> getByType(Type type) {
        List<Notification> notifications = getAll();
        ArrayList<Notification> list = new ArrayList();
        for (Notification notification : notifications) {
            if (notification.getType() == type) {
                list.add(notification);
            }
        }
        return list;
    }

    public List<Notification> getBy(Type type, List<Integer> ids) {
        ArrayList<Notification> notifications = new ArrayList();
        for (Integer intValue : ids) {
            Notification notification = get(intValue.intValue());
            if (notification != null && notification.isScheduled()) {
                notifications.add(notification);
            }
        }
        return notifications;
    }

    public boolean exist(int id) {
        return get(id) != null;
    }

    public boolean exist(int id, Type type) {
        Notification notification = get(id);
        return notification != null && notification.getType() == type;
    }

    public List<JSONObject> getOptions() {
        return getOptionsById(getIds());
    }

    public List<JSONObject> getOptionsById(List<Integer> ids) {
        ArrayList<JSONObject> options = new ArrayList();
        for (Integer intValue : ids) {
            Notification notification = get(intValue.intValue());
            if (notification != null) {
                options.add(notification.getOptions().getDict());
            }
        }
        return options;
    }

    public List<JSONObject> getOptionsByType(Type type) {
        ArrayList<JSONObject> options = new ArrayList();
        for (Notification notification : getByType(type)) {
            options.add(notification.getOptions().getDict());
        }
        return options;
    }

    public List<JSONObject> getOptionsBy(Type type, List<Integer> ids) {
        ArrayList<JSONObject> options = new ArrayList();
        for (Notification notification : getByIds(ids)) {
            if (notification.getType() == type) {
                options.add(notification.getOptions().getDict());
            }
        }
        return options;
    }

    public Notification get(int id) {
        Map<String, ?> alarms = getPrefs().getAll();
        String notId = Integer.toString(id);
        if (!alarms.containsKey(notId)) {
            return null;
        }
        try {
            return new Builder(this.context, new JSONObject(alarms.get(notId).toString())).build();
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }

    private JSONObject mergeJSONObjects(JSONObject obj1, JSONObject obj2) {
        Iterator it = obj2.keys();
        while (it.hasNext()) {
            try {
                String key = (String) it.next();
                obj1.put(key, obj2.opt(key));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return obj1;
    }

    private SharedPreferences getPrefs() {
        return this.context.getSharedPreferences("LocalNotification", 0);
    }

    private NotificationManager getNotMgr() {
        return (NotificationManager) this.context.getSystemService("notification");
    }
}
