package com.mk15.analyzer;

import android.app.Activity;
import android.os.Bundle;
import android.os.Build;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.view.InputDevice;
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
    boolean captureInput = false;
    final ArrayList<String> inputEvents = new ArrayList<>();

    public void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(1);
        root.setPadding(22,22,22,22);
        TextView title = new TextView(this);
        title.setText("MK15 Controller Analyzer — Extended");
        title.setTextSize(22);
        root.addView(title);
        TextView hint = new TextView(this);
        hint.setText("Расширенная диагностика. Сначала запусти тест ввода, подвигай стики, затем останови и собери отчёт.");
        root.addView(hint);
        Button startInput = new Button(this);
        startInput.setText("Начать тест стиков / ввода");
        root.addView(startInput);
        Button stopInput = new Button(this);
        stopInput.setText("Остановить тест ввода");
        root.addView(stopInput);
        Button scan = new Button(this);
        scan.setText("Собрать расширенный отчёт");
        root.addView(scan);
        Button share = new Button(this);
        share.setText("Отправить отчёт через Telegram");
        root.addView(share);
        Button save = new Button(this);
        save.setText("Сохранить TXT");
        root.addView(save);
        Button updates = new Button(this);
        updates.setText("Проверить обновления");
        root.addView(updates);
        output = new TextView(this);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(output);
        root.addView(scroll, new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);

        startInput.setOnClickListener(v -> {
            inputEvents.clear();
            captureInput = true;
            output.setText("Тест запущен. Подвигай физические стики и органы управления 10–20 секунд, затем нажми «Остановить тест ввода».");
        });
        stopInput.setOnClickListener(v -> {
            captureInput = false;
            output.setText("Тест остановлен. Записано событий движения: "+inputEvents.size());
        });
        scan.setOnClickListener(v -> { report = collect(); output.setText(report); });
        share.setOnClickListener(v -> {
            if (report.isEmpty()) report = collect();
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_SUBJECT, "MK15 Extended Analyzer report");
            i.putExtra(Intent.EXTRA_TEXT, report);
            startActivity(Intent.createChooser(i, "Отправить отчёт"));
        });
        save.setOnClickListener(v -> {
            if (report.isEmpty()) report = collect();
            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_TITLE, "MK15_Extended_Analyzer_Report.txt");
            startActivityForResult(i, 7);
        });
        updates.setOnClickListener(v -> UpdateManager.checkForUpdates(this, true));
        output.setText("Готово к диагностике. Для проверки стиков сначала запусти тест ввода.");
        UpdateManager.checkForUpdates(this, false);
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent e) {
        if (captureInput && e.getAction()==MotionEvent.ACTION_MOVE && inputEvents.size()<300) {
            InputDevice d=e.getDevice();
            StringBuilder line=new StringBuilder();
            line.append("t=").append(System.currentTimeMillis())
                .append(" deviceId=").append(e.getDeviceId())
                .append(" device=").append(d==null?"unknown":d.getName())
                .append(" source=0x").append(Integer.toHexString(e.getSource()))
                .append(" action=").append(e.getAction());
            for(int axis=0;axis<=15;axis++) {
                float value=e.getAxisValue(axis);
                if(value!=0f) line.append(" axis").append(axis).append("=").append(value);
            }
            inputEvents.add(line.toString());
        }
        return super.onGenericMotionEvent(e);
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
        s.append("MK15 CONTROLLER ANALYZER — EXTENDED\n");
        s.append("Date: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(new Date())).append("\n");
        s.append("Read-only diagnostics. No network access, no settings changes, no serial-port access.\n\n");
        s.append("=== DEVICE ===\n");
        line(s,"Manufacturer",Build.MANUFACTURER); line(s,"Brand",Build.BRAND);
        line(s,"Model",Build.MODEL); line(s,"Device",Build.DEVICE);
        line(s,"Product",Build.PRODUCT); line(s,"Hardware",Build.HARDWARE);
        line(s,"Board",Build.BOARD); line(s,"Android",Build.VERSION.RELEASE);
        line(s,"SDK",String.valueOf(Build.VERSION.SDK_INT)); line(s,"Build",Build.DISPLAY);
        line(s,"Fingerprint",Build.FINGERPRINT);
        s.append("\n=== INPUT DEVICES / AXES (CURRENTLY EXPOSED) ===\n");
        for(int id:InputDevice.getDeviceIds()) {
            InputDevice d=InputDevice.getDevice(id);
            if(d==null) continue;
            s.append("ID ").append(id).append(": ").append(d.getName()).append("\n");
            s.append("Descriptor: ").append(d.getDescriptor()).append("\n");
            s.append("Sources: 0x").append(Integer.toHexString(d.getSources())).append("\n");
            for(InputDevice.MotionRange r:d.getMotionRanges())
                s.append("Axis ").append(r.getAxis()).append(" source=0x").append(Integer.toHexString(r.getSource()))
                 .append(" min=").append(r.getMin()).append(" max=").append(r.getMax())
                 .append(" flat=").append(r.getFlat()).append(" fuzz=").append(r.getFuzz()).append("\n");
        }
        s.append("\n=== USB DEVICES (BASIC) ===\n");
        try {
            UsbManager u=(UsbManager)getSystemService(Context.USB_SERVICE);
            Map<String,UsbDevice> ds=u.getDeviceList();
            for(UsbDevice d:ds.values())
                s.append(d.getDeviceName()).append(" VID=").append(d.getVendorId())
                 .append(" PID=").append(d.getProductId()).append(" interfaces=").append(d.getInterfaceCount()).append("\n");
            if(ds.isEmpty()) s.append("No USB devices exposed to this app.\n");
        } catch(Exception e) { s.append("USB query failed: ").append(e.getClass().getSimpleName()).append("\n"); }

        s.append("\n=== SIYI PACKAGE (BASIC) ===\n");
        try {
            PackageInfo p=getPackageManager().getPackageInfo("com.example.v1.remotecontrol", PackageManager.GET_SERVICES|PackageManager.GET_PERMISSIONS);
            line(s,"Package",p.packageName); line(s,"Version",p.versionName);
            line(s,"UID",String.valueOf(p.applicationInfo.uid)); line(s,"APK path",p.applicationInfo.sourceDir);
            if(p.services!=null) for(ServiceInfo x:p.services)
                s.append("Service ").append(x.name).append(" exported=").append(x.exported)
                 .append(" permission=").append(x.permission).append("\n");
            if(p.requestedPermissions!=null) for(String x:p.requestedPermissions) s.append("Permission: ").append(x).append("\n");
        } catch(Exception e) { s.append("SIYI package not visible: ").append(e.getClass().getSimpleName()).append("\n"); }

        s.append("\n=== SIYI SERVICE ACTIONS (BASIC) ===\n");
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

        s.append("\n=== LIVE INPUT TEST ===\n");
        s.append("Captured motion events: ").append(inputEvents.size()).append("\n");
        if(inputEvents.isEmpty()) s.append("No generic motion events captured during the test. This does NOT prove the MCU has no stick data; it means Android did not deliver generic motion events to this app during that test.\n");
        for(String event:inputEvents) s.append(event).append("\n");

        ExtendedDiagnostics.append(this,s);
        UpdateManager.appendDiagnostics(this,s);

        s.append("\n=== LIMITATIONS / INTERPRETATION ===\n");
        s.append("This app only sees information exposed to an ordinary Android application. No private SIYI app memory, protected system data, serial port bytes, /dev/ttyHS1, radio data, credentials, messages, location or Wi-Fi passwords are read. A service listed by PackageManager is not proof that third-party binding is permitted. Empty live-input results do not rule out stick data inside the SIYI app or MCU. Actual J1-J4 values require a documented/accessible SIYI API or a safe supported input interface.\n");
        return s.toString();
    }
    void line(StringBuilder s,String k,String v){s.append(k).append(": ").append(v==null?"(null)":v).append("\n");}
}
