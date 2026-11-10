package com.mk15.analyzer;

import android.app.Activity;
import android.os.Bundle;
import android.os.Build;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.hardware.input.InputDevice;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.view.MotionEvent;
import android.widget.*;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {
    TextView output;
    String report = "";

    public void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(1);
        root.setPadding(22,22,22,22);
        TextView title = new TextView(this);
        title.setText("MK15 Controller Analyzer");
        title.setTextSize(23);
        root.addView(title);
        Button scan = new Button(this);
        scan.setText("Собрать отчёт");
        root.addView(scan);
        Button share = new Button(this);
        share.setText("Отправить через Telegram");
        root.addView(share);
        Button save = new Button(this);
        save.setText("Сохранить TXT");
        root.addView(save);
        output = new TextView(this);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(output);
        root.addView(scroll, new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);

        scan.setOnClickListener(v -> { report = collect(); output.setText(report); });
        share.setOnClickListener(v -> {
            if (report.isEmpty()) report = collect();
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_SUBJECT, "MK15 Analyzer report");
            i.putExtra(Intent.EXTRA_TEXT, report);
            startActivity(Intent.createChooser(i, "Отправить отчёт"));
        });
        save.setOnClickListener(v -> {
            if (report.isEmpty()) report = collect();
            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_TITLE, "MK15_Analyzer_Report.txt");
            startActivityForResult(i, 7);
        });
    }

    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req,res,data);
        if(req==7 && res==RESULT_OK && data!=null) {
            try(OutputStream os=getContentResolver().openOutputStream(data.getData())) {
                os.write(report.getBytes(StandardCharsets.UTF_8));
                output.setText("Отчёт сохранён.");
            } catch(Exception e) { output.setText("Ошибка сохранения: "+e); }
        }
    }

    String collect() {
        StringBuilder s = new StringBuilder();
        s.append("MK15 CONTROLLER ANALYZER\n");
        s.append("Date: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(new Date())).append("\n");
        s.append("Read-only diagnostic report. No network access, no settings changes.\n\n");
        s.append("=== DEVICE ===\n");
        line(s,"Manufacturer",Build.MANUFACTURER); line(s,"Brand",Build.BRAND);
        line(s,"Model",Build.MODEL); line(s,"Device",Build.DEVICE);
        line(s,"Product",Build.PRODUCT); line(s,"Hardware",Build.HARDWARE);
        line(s,"Board",Build.BOARD); line(s,"Android",Build.VERSION.RELEASE);
        line(s,"SDK",String.valueOf(Build.VERSION.SDK_INT)); line(s,"Build",Build.DISPLAY);
        line(s,"Fingerprint",Build.FINGERPRINT);
        s.append("\n=== INPUT DEVICES / AXES ===\n");
        for(int id:InputDevice.getDeviceIds()) {
            InputDevice d=InputDevice.getDevice(id);
            if(d==null) continue;
            s.append("ID ").append(id).append(": ").append(d.getName()).append("\n");
            s.append("Descriptor: ").append(d.getDescriptor()).append("\n");
            s.append("Sources: 0x").append(Integer.toHexString(d.getSources())).append("\n");
            for(InputDevice.MotionRange r:d.getMotionRanges())
                s.append("Axis ").append(r.getAxis()).append(" min=").append(r.getMin())
                 .append(" max=").append(r.getMax()).append(" flat=").append(r.getFlat()).append("\n");
        }
        s.append("\n=== USB DEVICES ===\n");
        try {
            UsbManager u=(UsbManager)getSystemService(Context.USB_SERVICE);
            Map<String,UsbDevice> ds=u.getDeviceList();
            for(UsbDevice d:ds.values())
                s.append(d.getDeviceName()).append(" VID=").append(d.getVendorId())
                 .append(" PID=").append(d.getProductId()).append(" interfaces=").append(d.getInterfaceCount()).append("\n");
            if(ds.isEmpty()) s.append("No USB devices exposed to this app.\n");
        } catch(Exception e) { s.append("USB query failed: ").append(e.getClass().getSimpleName()).append("\n"); }

        s.append("\n=== SIYI PACKAGE ===\n");
        try {
            PackageInfo p=getPackageManager().getPackageInfo("com.example.v1.remotecontrol", PackageManager.GET_SERVICES|PackageManager.GET_PERMISSIONS);
            line(s,"Package",p.packageName); line(s,"Version",p.versionName);
            line(s,"UID",String.valueOf(p.applicationInfo.uid)); line(s,"APK path",p.applicationInfo.sourceDir);
            if(p.services!=null) for(ServiceInfo x:p.services)
                s.append("Service ").append(x.name).append(" exported=").append(x.exported)
                 .append(" permission=").append(x.permission).append("\n");
            if(p.requestedPermissions!=null) for(String x:p.requestedPermissions) s.append("Permission: ").append(x).append("\n");
        } catch(Exception e) { s.append("SIYI package not visible: ").append(e.getClass().getSimpleName()).append("\n"); }

        s.append("\n=== SIYI SERVICE ACTIONS ===\n");
        for(String action:new String[]{"com.example.mcuservice.action.MainService","com.example.mcuservice.action.RemoteControlService"}) {
            List<android.content.pm.ResolveInfo> found=getPackageManager().queryIntentServices(new Intent(action),0);
            s.append(action).append(": ").append(found.size()).append(" result(s)\n");
            for(android.content.pm.ResolveInfo x:found) if(x.serviceInfo!=null)
                s.append("  ").append(x.serviceInfo.packageName).append("/").append(x.serviceInfo.name)
                 .append(" exported=").append(x.serviceInfo.exported).append(" permission=").append(x.serviceInfo.permission).append("\n");
        }

        s.append("\n=== INSTALLED PACKAGES ===\n");
        try {
            List<PackageInfo> ps=getPackageManager().getInstalledPackages(0);
            Collections.sort(ps,(a,b)->a.packageName.compareToIgnoreCase(b.packageName));
            for(PackageInfo p:ps) s.append(p.packageName).append("\n");
            s.append("Count: ").append(ps.size()).append("\n");
        } catch(Exception e) { s.append("Package listing unavailable: ").append(e.getClass().getSimpleName()).append("\n"); }

        s.append("\n=== LIMITATIONS ===\n");
        s.append("This app cannot inspect protected system data or private app data. It does not read serial ports, /dev/ttyHS1, radio data, credentials, messages, location or Wi-Fi passwords. Android may restrict package inventory and service visibility. Live stick values require an accessible SIYI API or input device.\n");
        return s.toString();
    }
    void line(StringBuilder s,String k,String v){s.append(k).append(": ").append(v==null?"(null)":v).append("\n");}
}
