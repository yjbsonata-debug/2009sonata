package kr.sonatadrive.app;

import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    private BluetoothSocket socket;
    private OutputStream out;
    private InputStream in;
    private final Handler handler = new Handler();
    private final UUID SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private TextView speed, rpm, fuel, trip, score, temp, obdStatus, dtcText, tip;
    private double tripKm = 0, fuelSum = 0;
    private long lastSample = 0;
    private int harshAccel = 0, harshBrake = 0;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        speed = findViewById(R.id.speed); rpm = findViewById(R.id.rpm);
        fuel = findViewById(R.id.fuel); trip = findViewById(R.id.trip);
        score = findViewById(R.id.score); temp = findViewById(R.id.temp);
        obdStatus = findViewById(R.id.obdStatus); dtcText = findViewById(R.id.dtcText); tip = findViewById(R.id.tip);

        findViewById(R.id.connectBtn).setOnClickListener(v -> connect());
        findViewById(R.id.readDtcBtn).setOnClickListener(v -> readDtc());
        findViewById(R.id.clearDtcBtn).setOnClickListener(v -> confirmClearDtc());

        requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 10);
    }

    private void connect() {
        new Thread(() -> {
            try {
                BluetoothAdapter ba = BluetoothAdapter.getDefaultAdapter();
                if (ba == null) throw new IOException("Bluetooth 미지원");
                BluetoothDevice target = null;
                for (BluetoothDevice d : ba.getBondedDevices()) {
                    String n = d.getName() == null ? "" : d.getName().toUpperCase(Locale.US);
                    if (n.contains("OBD") || n.contains("XTOOL") || n.contains("ELM")) { target = d; break; }
                }
                if (target == null) throw new IOException("페어링된 OBD 장치를 찾지 못했습니다.");
                socket = target.createRfcommSocketToServiceRecord(SPP);
                socket.connect();
                out = socket.getOutputStream(); in = socket.getInputStream();
                cmd("ATZ"); cmd("ATE0"); cmd("ATL0"); cmd("ATS0"); cmd("ATSP0");
                runOnUiThread(() -> obdStatus.setText("● OBD 연결됨"));
                poll();
            } catch (Exception e) {
                runOnUiThread(() -> obdStatus.setText("● 연결 실패: " + e.getMessage()));
            }
        }).start();
    }

    private void poll() {
        handler.postDelayed(() -> {
            if (socket == null || !socket.isConnected()) return;
            try {
                String s = clean(cmd("010D")); // speed
                String r = clean(cmd("010C")); // rpm
                String t = clean(cmd("0105")); // coolant
                int v = parseSingleBytePid(s, "410D");
                int rr = parseRpm(r);
                int tt = parseSingleBytePid(t, "4105") - 40;
                runOnUiThread(() -> {
                    speed.setText(String.valueOf(v));
                    rpm.setText(String.valueOf(rr));
                    temp.setText(String.valueOf(tt));
                    updateScore(v, rr);
                });
            } catch (Exception ignored) {}
            poll();
        }, 1200);
    }

    private void updateScore(int v, int rr) {
        if (lastSample > 0) {
            // Prototype heuristic; final version will use calibrated trip history.
            if (rr > 3500) harshAccel++;
            if (v > 120) harshBrake++;
        }
        lastSample = System.currentTimeMillis();
        int sc = Math.max(0, 100 - harshAccel * 2 - harshBrake * 3);
        score.setText(String.valueOf(sc));
        if (rr > 3500) tip.setText("💡 TIP  고RPM 구간을 줄이면 연비 개선에 도움이 됩니다.");
        else if (v > 110) tip.setText("💡 TIP  일정한 속도를 유지하면 연비에 유리합니다.");
        else tip.setText("💡 TIP  부드러운 가속과 충분한 차간거리를 유지하세요.");
    }

    private void readDtc() {
        new Thread(() -> {
            try {
                String raw = clean(cmd("03"));
                List<String> codes = decodeDtc(raw);
                runOnUiThread(() -> dtcText.setText(codes.isEmpty() ? "저장된 고장코드 없음" : "감지 코드\n" + android.text.TextUtils.join("\n", codes)));
            } catch (Exception e) {
                runOnUiThread(() -> dtcText.setText("DTC 읽기 실패\n" + e.getMessage()));
            }
        }).start();
    }

    private void confirmClearDtc() {
        new AlertDialog.Builder(this)
            .setTitle("고장코드 삭제")
            .setMessage("저장된 DTC와 진단 관련 데이터가 초기화될 수 있습니다. P2096이 실제로 해결된 것은 아닙니다. 삭제할까요?")
            .setNegativeButton("취소", null)
            .setPositiveButton("삭제", (d,w) -> clearDtc()).show();
    }

    private void clearDtc() {
        new Thread(() -> {
            try {
                String raw = clean(cmd("04"));
                runOnUiThread(() -> {
                    dtcText.setText("DTC 삭제 명령 전송 완료\n재시동 후 경고등 재발 여부를 확인하세요.");
                    Toast.makeText(this, "코드 삭제 명령을 보냈습니다.", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> dtcText.setText("DTC 삭제 실패\n" + e.getMessage()));
            }
        }).start();
    }

    private String cmd(String c) throws Exception {
        if (out == null || in == null) throw new IOException("OBD 연결 안 됨");
        out.write((c + "\r").getBytes(StandardCharsets.US_ASCII)); out.flush();
        long end = System.currentTimeMillis() + 1800;
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        while (System.currentTimeMillis() < end) {
            while (in.available() > 0) b.write(in.read());
            if (b.size() > 0 && b.toString("US-ASCII").contains(">")) break;
            Thread.sleep(20);
        }
        return b.toString("US-ASCII");
    }

    private String clean(String s) { return s.replace("\r"," ").replace("\n"," ").replace(">"," ").trim(); }

    private int parseSingleBytePid(String s, String header) {
        int i = s.indexOf(header);
        if (i < 0) return 0;
        String[] p = s.substring(i + header.length()).trim().split("\\s+");
        return p.length == 0 ? 0 : Integer.parseInt(p[0], 16);
    }

    private int parseRpm(String s) {
        int i = s.indexOf("410C");
        if (i < 0) return 0;
        String[] p = s.substring(i + 4).trim().split("\\s+");
        if (p.length < 2) return 0;
        return ((Integer.parseInt(p[0],16) * 256) + Integer.parseInt(p[1],16)) / 4;
    }

    private List<String> decodeDtc(String s) {
        ArrayList<String> out = new ArrayList<>();
        int i = s.indexOf("43");
        if (i < 0) return out;
        String[] p = s.substring(i + 2).trim().split("\\s+");
        for (int j=0; j+1<p.length; j+=2) {
            int a = Integer.parseInt(p[j],16), b = Integer.parseInt(p[j+1],16);
            if (a==0 && b==0) continue;
            String[] lead = {"P","C","B","U"};
            String code = lead[(a >> 6) & 3] + ((a >> 4) & 3) + String.format(Locale.US,"%X%02X", a & 15, b);
            out.add(code);
        }
        return out;
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        try { if (socket != null) socket.close(); } catch(Exception ignored) {}
        super.onDestroy();
    }
}
