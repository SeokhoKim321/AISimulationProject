// ==========================
// UdpConnector.java
// ==========================

package com.example.ai;

import java.net.DatagramPacket;
import java.net.DatagramSocket;

// Thread(스레드)를 상속받습니다. (백그라운드에서 혼자 독자적으로 움직인다는 뜻입니다)
public class UdpConnector extends Thread {

    private Aircraft targetAircraft; // 데이터를 집어넣을 대상 비행기
    private int port;                // 무전기 주파수 (포트 번호)
    private boolean running = true;  // 통신병의 근무 상태 (true면 계속 근무)

    // 생성자 (통신병을 처음 배치할 때 비행기와 주파수를 지정해줍니다)
    public UdpConnector(Aircraft aircraft, int port) {
        this.targetAircraft = aircraft;
        this.port = port;
    }

    // 통신병이 배치되면 무한 반복해서 수행할 핵심 임무입니다.
    @Override
    public void run() {
        try {
            // 1. 지정된 주파수로 무전기를 켭니다.
            DatagramSocket socket = new DatagramSocket(port);
            byte[] receiveData = new byte[1024]; // 편지봉투 크기 설정

            System.out.println("MATLAB 통신병 수신 대기 중... (주파수: " + port + ")");

            // 2. running이 true인 동안 무한히 반복해서 무전을 듣습니다.
            while (running) {
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);

                // 여기서 무전이 올 때까지 프로그램이 멈춰서 귀를 기울입니다.
                socket.receive(receivePacket);

                // 3. 무전이 오면 전기 신호를 사람이 읽을 수 있는 글자(String)로 바꿉니다.
                String message = new String(receivePacket.getData(), 0, receivePacket.getLength()).trim();

                // 4. MATLAB이 "피치,롤,요,속도,RPM" 형태로 보낼 것이므로 쉼표(,)를 기준으로 글자를 자릅니다.
                String[] values = message.split(",");

                // 5. 잘라낸 조각이 5개라면, 글자를 숫자(Double)로 번역해서 비행기 변수에 집어넣습니다.
                if (values.length >= 5) {
                    targetAircraft.setPitch(Double.parseDouble(values[0]));
                    targetAircraft.setRoll(Double.parseDouble(values[1]));
                    targetAircraft.setYaw(Double.parseDouble(values[2]));
                    targetAircraft.setIndicatedAirspeed(Double.parseDouble(values[3]));
                    targetAircraft.setEngineRPM(Double.parseDouble(values[4]));

                    // --- [여기에 아래 1줄을 추가하십시오] ---
                    System.out.println("MATLAB 데이터 수신 완료 -> " + message);
                }


            }
            socket.close(); // 근무가 끝나면 무전기를 끕니다.

        } catch (Exception e) {
            System.out.println("통신망에 문제가 발생했습니다.");
            e.printStackTrace();
        }
    }

    // 근무를 강제로 종료시킬 때 부르는 스위치입니다.
    public void stopListening() {
        running = false;
    }
}