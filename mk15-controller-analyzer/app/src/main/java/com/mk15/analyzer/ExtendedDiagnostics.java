package com.mk15.analyzer;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.display.DisplayManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Debug;
import android.os.Environment;
import android.os.StatFs;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.ViewConfiguration;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

final class ExtendedDiagnostics {
    private ExtendedDiagnostics() {}

    static void append(Context c, StringBuilder s) {
        s.append("\n=== EXTENDED SYSTEM ===\n");
        line(s,"Java runtime",System.getProperty("java.runtime.version"));
        line(s,"OS version",System.getProperty("os.version"));
        line(s,"Supported ABIs",join(Build.SUPPORTED_ABIS));
        line(s,"32-bit ABIs",join(Build.SUPPORTED_32_BIT_ABIS));
        line(s,"64-bit ABIs",join(Build.SUPPORTED_64_BIT_ABIS));
        line(s,"Bootloader",Build.BOOTLOADER);
        line(s,"Radio version",Build.getRadioVersion());
        line(s,"Time zone",TimeZone.getDefault().getID());
        line(s,"Locale",Locale.getDefault().toLanguageTag());
        line(s,"Uptime ms",String.valueOf(android.os.SystemClock.uptimeMillis()));
        line(s,"Elapsed realtime ms",String.valueOf(android.os.SystemClock.elapsedRealtime()));
        line(s,"Debug build",String.valueOf(Build.TYPE));
        line(s,"Tags",Build.TAGS);

        s.append("\n=== DISPLAY / CONFIGURATION ===\n");
        try {
            DisplayMetrics m=c.getResources().getDisplayMetrics();
            line(s,"Resolution px",m.widthPixels+" x "+m.heightPixels);
            line(s,"Density dpi",String.valueOf(m.densityDpi));
            line(s,"Density scale",String.valueOf(m.density));
            line(s,"Scaled density",String.valueOf(m.scaledDensity));
            line(s,"Refresh rate Hz",String.valueOf(((android.view.WindowManager)c.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRefreshRate()));
            line(s,"Rotation",String.valueOf(((android.view.WindowManager)c.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRotation()));
            Configuration cfg=c.getResources().getConfiguration();
            line(s,"Orientation",String.valueOf(cfg.orientation));
            line(s,"Screen layout",String.valueOf(cfg.screenLayout));
            line(s,"UI mode",String.valueOf(cfg.uiMode));
            line(s,"Font scale",String.valueOf(cfg.fontScale));
            line(s,"Touchscreen config",String.valueOf(c.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)));
        } catch(Exception e) { error(s,"Display",e); }

        s.append("\n=== MEMORY ===\n");
        try {
            android.app.ActivityManager am=(android.app.ActivityManager)c.getSystemService(Context.ACTIVITY_SERVICE);
            android.app.ActivityManager.MemoryInfo mi=new android.app.ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            line(s,"Total RAM bytes",String.valueOf(mi.totalMem));
            line(s,"Available RAM bytes",String.valueOf(mi.availMem));
            line(s,"Low memory threshold bytes",String.valueOf(mi.threshold));
            line(s,"Low memory",String.valueOf(mi.lowMemory));
            line(s,"App Java max memory bytes",String.valueOf(Runtime.getRuntime().maxMemory()));
            line(s,"App Java total memory bytes",String.valueOf(Runtime.getRuntime().totalMemory()));
            line(s,"App Java free memory bytes",String.valueOf(Runtime.getRuntime().freeMemory()));
            Debug.MemoryInfo dmi=new Debug.MemoryInfo();
            Debug.getMemoryInfo(dmi);
            line(s,"App PSS KB",String.valueOf(dmi.getTotalPss()));
        } catch(Exception e) { error(s,"Memory",e); }

        s.append("\n=== STORAGE ===\n");
        storage(s,"Data directory",Environment.getDataDirectory());
        storage(s,"External storage",Environment.getExternalStorageDirectory());
        line(s,"External storage state",Environment.getExternalStorageState());

        s.append("\n=== BATTERY ===\n");
        try {
            Intent b=c.registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if(b!=null) {
                int level=b.getIntExtra(BatteryManager.EXTRA_LEVEL,-1);
                int scale=b.getIntExtra(BatteryManager.EXTRA_SCALE,-1);
                line(s,"Level percent",level>=0&&scale>0?String.valueOf(level*100/scale):"unknown");
                line(s,"Status",String.valueOf(b.getIntExtra(BatteryManager.EXTRA_STATUS,-1)));
                line(s,"Health",String.valueOf(b.getIntExtra(BatteryManager.EXTRA_HEALTH,-1)));
                line(s,"Plugged",String.valueOf(b.getIntExtra(BatteryManager.EXTRA_PLUGGED,-1)));
                line(s,"Voltage mV",String.valueOf(b.getIntExtra(BatteryManager.EXTRA_VOLTAGE,-1)));
                line(s,"Temperature tenths C",String.valueOf(b.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,-1)));
                line(s,"Technology",b.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY));
            } else s.append("Battery broadcast unavailable\n");
        } catch(Exception e) { error(s,"Battery",e); }

        s.append("\n=== SENSORS ===\n");
        try {
            SensorManager sm=(SensorManager)c.getSystemService(Context.SENSOR_SERVICE);
            List<Sensor> sensors=sm.getSensorList(Sensor.TYPE_ALL);
            for(Sensor x:sensors) {
                s.append(x.getName()).append(" | vendor=").append(x.getVendor())
                 .append(" | type=").append(x.getType()).append(" | version=").append(x.getVersion())
                 .append(" | wakeup=").append(x.isWakeUpSensor())
                 .append(" | minDelayUs=").append(x.getMinDelay()).append("\n");
            }
            line(s,"Sensor count",String.valueOf(sensors.size()));
        } catch(Exception e) { error(s,"Sensors",e); }

        s.append("\n=== ANDROID FEATURES ===\n");
        try {
            for(android.content.pm.FeatureInfo f:c.getPackageManager().getSystemAvailableFeatures())
                s.append(f.name==null?"(unnamed feature)":f.name).append(" version=").append(f.version)
                 .append(" GLES=").append(f.reqGlEsVersion).append("\n");
        } catch(Exception e) { error(s,"Features",e); }

        s.append("\n=== INPUT DEVICE DETAILS ===\n");
        try {
            line(s,"Touch slop px",String.valueOf(ViewConfiguration.get(c).getScaledTouchSlop()));
            line(s,"Has permanent menu key",String.valueOf(ViewConfiguration.get(c).hasPermanentMenuKey()));
            line(s,"Virtual keyboard present",String.valueOf(KeyCharacterMap.deviceHasKey(android.view.KeyEvent.KEYCODE_BACK)));
            for(int id:InputDevice.getDeviceIds()) {
                InputDevice d=InputDevice.getDevice(id);
                if(d==null) continue;
                s.append("Device ").append(id).append(" ").append(d.getName())
                 .append(" vendorId=").append(d.getVendorId()).append(" productId=").append(d.getProductId())
                 .append(" keyboardType=").append(d.getKeyboardType())
                 .append(" virtual=").append(d.isVirtual()).append("\n");
                for(InputDevice.MotionRange r:d.getMotionRanges())
                    s.append("  axis=").append(r.getAxis()).append(" source=0x")
                     .append(Integer.toHexString(r.getSource())).append(" min=").append(r.getMin())
                     .append(" max=").append(r.getMax()).append(" flat=").append(r.getFlat())
                     .append(" fuzz=").append(r.getFuzz()).append("\n");
            }
        } catch(Exception e) { error(s,"Input details",e); }

        s.append("\n=== USB DESCRIPTORS ===\n");
        try {
            UsbManager um=(UsbManager)c.getSystemService(Context.USB_SERVICE);
            Map<String,UsbDevice> devices=um.getDeviceList();
            for(UsbDevice d:devices.values()) {
                s.append("USB ").append(d.getDeviceName()).append(" VID=").append(hex(d.getVendorId()))
                 .append(" PID=").append(hex(d.getProductId())).append(" class=").append(d.getDeviceClass())
                 .append(" subclass=").append(d.getDeviceSubclass()).append(" protocol=").append(d.getDeviceProtocol())
                 .append(" interfaces=").append(d.getInterfaceCount()).append("\n");
                for(int i=0;i<d.getInterfaceCount();i++) {
                    UsbInterface in=d.getInterface(i);
                    s.append("  interface ").append(i).append(" id=").append(in.getId())
                     .append(" class=").append(in.getInterfaceClass()).append(" subclass=").append(in.getInterfaceSubclass())
                     .append(" protocol=").append(in.getInterfaceProtocol()).append(" endpoints=").append(in.getEndpointCount()).append("\n");
                    for(int j=0;j<in.getEndpointCount();j++) {
                        UsbEndpoint ep=in.getEndpoint(j);
                        s.append("    endpoint address=").append(hex(ep.getAddress()))
                         .append(" direction=").append(ep.getDirection()).append(" type=").append(ep.getType())
                         .append(" maxPacket=").append(ep.getMaxPacketSize()).append("\n");
                    }
                }
            }
            if(devices.isEmpty()) s.append("No USB devices currently exposed.\n");
        } catch(Exception e) { error(s,"USB descriptors",e); }

        s.append("\n=== SIYI PACKAGE DETAILS ===\n");
        try {
            PackageManager pm=c.getPackageManager();
            PackageInfo p=pm.getPackageInfo("com.example.v1.remotecontrol",
                PackageManager.GET_SERVICES|PackageManager.GET_PERMISSIONS|PackageManager.GET_ACTIVITIES|PackageManager.GET_RECEIVERS|PackageManager.GET_PROVIDERS);
            line(s,"SIYI firstInstallTime",new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.US).format(new Date(p.firstInstallTime)));
            line(s,"SIYI lastUpdateTime",new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.US).format(new Date(p.lastUpdateTime)));
            line(s,"SIYI target SDK",String.valueOf(p.applicationInfo.targetSdkVersion));
            line(s,"SIYI enabled",String.valueOf(p.applicationInfo.enabled));
            line(s,"SIYI flags",Integer.toHexString(p.applicationInfo.flags));
            line(s,"SIYI data dir",p.applicationInfo.dataDir);
            line(s,"SIYI native library dir",p.applicationInfo.nativeLibraryDir);
            line(s,"SIYI process",p.applicationInfo.processName);
            line(s,"SIYI source/public source",p.applicationInfo.sourceDir+" | "+p.applicationInfo.publicSourceDir);
            if(p.activities!=null) for(android.content.pm.ActivityInfo a:p.activities)
                s.append("Activity ").append(a.name).append(" exported=").append(a.exported).append(" permission=").append(a.permission).append("\n");
            if(p.receivers!=null) for(android.content.pm.ActivityInfo a:p.receivers)
                s.append("Receiver ").append(a.name).append(" exported=").append(a.exported).append(" permission=").append(a.permission).append("\n");
            if(p.providers!=null) for(android.content.pm.ProviderInfo a:p.providers)
                s.append("Provider ").append(a.name).append(" exported=").append(a.exported).append(" permission=").append(a.readPermission).append("\n");
            if(p.services!=null) for(android.content.pm.ServiceInfo a:p.services)
                s.append("Service ").append(a.name).append(" exported=").append(a.exported).append(" permission=").append(a.permission).append(" process=").append(a.processName).append("\n");
            if(p.requestedPermissions!=null) for(String perm:p.requestedPermissions)
                s.append("Requested permission ").append(perm).append("\n");
        } catch(Exception e) { error(s,"SIYI package details",e); }

        s.append("\n=== SIYI ACTION RESOLUTION ===\n");
        for(String action:new String[]{"com.example.mcuservice.action.MainService","com.example.mcuservice.action.RemoteControlService"}) {
            try {
                List<ResolveInfo> found=c.getPackageManager().queryIntentServices(new Intent(action),0);
                s.append(action).append(" matches=").append(found.size()).append("\n");
                for(ResolveInfo r:found) if(r.serviceInfo!=null)
                    s.append("  ").append(r.serviceInfo.packageName).append("/")
                     .append(r.serviceInfo.name).append(" exported=").append(r.serviceInfo.exported)
                     .append(" permission=").append(r.serviceInfo.permission).append("\n");
            } catch(Exception e) { error(s,action,e); }
        }

        s.append("\n=== APP ENVIRONMENT ===\n");
        line(s,"App package",c.getPackageName());
        line(s,"App version",String.valueOf(getVersion(c)));
        line(s,"App data dir",c.getFilesDir().getAbsolutePath());
        line(s,"Process name",Build.VERSION.SDK_INT>=28?android.app.Application.getProcessName():"API<28");
        s.append("End of extended diagnostics. Values are limited to information Android exposes to an ordinary app.\n");
    }

    private static String getVersion(Context c) {
        try { return c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName; }
        catch(Exception e) { return "unknown"; }
    }
    private static void storage(StringBuilder s,String label,File f) {
        try {
            StatFs st=new StatFs(f.getAbsolutePath());
            line(s,label+" path",f.getAbsolutePath());
            line(s,label+" total bytes",String.valueOf(st.getTotalBytes()));
            line(s,label+" available bytes",String.valueOf(st.getAvailableBytes()));
            line(s,label+" free bytes",String.valueOf(st.getFreeBytes()));
        } catch(Exception e) { error(s,label,e); }
    }
    private static String join(String[] a) {
        if(a==null) return "unknown";
        StringBuilder b=new StringBuilder();
        for(String x:a) { if(b.length()>0)b.append(", "); b.append(x); }
        return b.toString();
    }
    private static String hex(int n) { return "0x"+Integer.toHexString(n); }
    private static void line(StringBuilder s,String k,String v) {
        s.append(k).append(": ").append(v==null?"(null)":v).append("\n");
    }
    private static void error(StringBuilder s,String where,Exception e) {
        s.append(where).append(" unavailable: ").append(e.getClass().getSimpleName()).append("\n");
    }
}
