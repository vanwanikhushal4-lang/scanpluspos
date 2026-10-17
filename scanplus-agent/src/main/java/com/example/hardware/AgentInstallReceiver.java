package com.example.hardware;

import android.content.*;
import android.content.pm.PackageInstaller;
import org.json.JSONObject;

/** Explicit, non-exported PackageInstaller callback. No installer UI is launched remotely. */
public final class AgentInstallReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        String token=intent.getStringExtra("scanplusToken");if(token==null)return;
        android.content.SharedPreferences results=context.getSharedPreferences("scanplus_installer",Context.MODE_PRIVATE);
        if(!results.getBoolean(token+".pending",false))return;
        try{String message=intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);if(message==null)message="";if(message.length()>1000)message=message.substring(0,1000);results.edit().putString(token,new JSONObject().put("status",intent.getIntExtra(PackageInstaller.EXTRA_STATUS,PackageInstaller.STATUS_FAILURE)).put("message",message).toString()).commit();}catch(Exception ignored){}
    }
}
