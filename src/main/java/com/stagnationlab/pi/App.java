package com.stagnationlab.pi;

import com.pi4j.io.gpio.*;
import com.pi4j.io.gpio.event.GpioPinDigitalStateChangeEvent;
import com.pi4j.io.gpio.event.GpioPinListenerDigital;
import com.pi4j.io.i2c.I2CBus;
import com.pi4j.io.i2c.I2CDevice;
import com.pi4j.io.i2c.I2CFactory;
import com.pi4j.system.NetworkInfo;
import com.pi4j.system.SystemInfo;
import com.pi4j.wiringpi.SoftPwm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Console;
import java.io.IOException;
import java.text.ParseException;

import static com.pi4j.wiringpi.Gpio.wiringPiSetup;

public class App {

    private static Logger log = LoggerFactory.getLogger(App.class);

    private final Pin RED_LED_PIN = RaspiPin.GPIO_22;
    private final Pin YELLOW_LED_PIN = RaspiPin.GPIO_23;
    private final Pin GREEN_LED_PIN = RaspiPin.GPIO_29;
    private final Pin BUTTON_PIN = RaspiPin.GPIO_21;
    private final Pin MOTION_PIN = RaspiPin.GPIO_00;

    private final GpioController gpio = GpioFactory.getInstance();
    private final GpioPinDigitalOutput redLedPin = gpio.provisionDigitalOutputPin(RED_LED_PIN, PinState.LOW);
    //private final GpioPinAnalogOutput yellowLedPin = gpio.provisionAnalogOutputPin(YELLOW_LED_PIN, 0);
    private final GpioPinDigitalOutput greenLedPin = gpio.provisionDigitalOutputPin(GREEN_LED_PIN, PinState.LOW);
    private final GpioPinDigitalInput buttonPin = gpio.provisionDigitalInputPin(BUTTON_PIN, PinPullResistance.PULL_UP);
    private final GpioPinDigitalInput motionPin = gpio.provisionDigitalInputPin(MOTION_PIN, PinPullResistance.PULL_DOWN);

    private boolean isRunning = false;

    private App() throws InterruptedException, ParseException, IOException {
        log.info("starting experiments");

        isRunning = true;

        showSystemInfo();
        showSetupInfo();

        setupButtonListener();
        setupMotionListener();

        testLed();

        try {
            testADC();
        } catch (Exception e) {
            e.printStackTrace();

            log.warn("testing i2c adc device failed (" + e.getMessage() + ")");
        }

        testDAC();

        waitForQuit();

        isRunning = false;

        //testLed();
        gpio.shutdown();

        log.info("shutdown successful");
    }

    private void testLed() throws InterruptedException {
        log.info("-- testing led --");

        redLedPin.setShutdownOptions(true, PinState.LOW);

        greenLedPin.high();
        log.info("> ON");
        Thread.sleep(1000);
        greenLedPin.low();
        log.info("> OFF");
    }

    private void testADC() throws IOException, I2CFactory.UnsupportedBusNumberException, InterruptedException {
        I2CBus bus = I2CFactory.getInstance(I2CBus.BUS_1);
        I2CDevice device = bus.getDevice(0x55);

        device.write(0x02, (byte)0x20);

        Thread.sleep(500);

        (new Thread(() -> {
            while (isRunning) {
                byte[] data = new byte[2];
                try {
                    device.read(0x00, data, 0, 2);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                // convert the data to 12-bits
                int value = ((data[0] & 0x0F) * 256 + (data[1] & 0xFF));

                log.info("digital value of analog input: " + value);

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        })).start();
    }

    private void testDAC() {
        final int pin = 23;
        final int max = 100;

        SoftPwm.softPwmCreate(pin, 0, max);

        (new Thread(() -> {
            float value = 0.0f;
            float step = 0.5f;

            while (isRunning) {
                //yellowLedPin.setValue(value);

                SoftPwm.softPwmWrite(pin, Math.round(value));

                if (value + step > max || value + step < 0) {
                    step *= -1.0f;
                }

                value += step;

                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        })).start();
    }

    private void setupButtonListener() throws InterruptedException {
        log.info("setting up button listener on pin " + buttonPin.getName());

        buttonPin.addListener(new GpioPinListenerDigital() {
            @Override
            public void handleGpioPinDigitalStateChangeEvent(GpioPinDigitalStateChangeEvent event) {
                if (event.getState() == PinState.LOW) {
                    log.info("button pressed");

                    greenLedPin.high();
                } else {
                    log.info("button released");

                    greenLedPin.low();
                }
            }
        });
    }

    private void setupMotionListener() throws InterruptedException {
        log.info("setting up motion listener on pin " + motionPin.getName());

        motionPin.addListener(new GpioPinListenerDigital() {
            @Override
            public void handleGpioPinDigitalStateChangeEvent(GpioPinDigitalStateChangeEvent event) {
                if (event.getState() == PinState.HIGH) {
                    log.info("motion detected");

                    redLedPin.high();
                } else {
                    log.info("motion reset");

                    redLedPin.low();
                }
            }
        });
    }

    private void showSetupInfo() {
        log.info("Led pin: " + YELLOW_LED_PIN.getName());
    }

    private void showSystemInfo() throws IOException, InterruptedException, ParseException {
        // display a few of the available system information properties
        log.info("----------------------------------------------------");
        log.info("HARDWARE INFO");
        log.info("----------------------------------------------------");
        log.info("Serial Number     :  " + SystemInfo.getSerial());
        log.info("CPU Revision      :  " + SystemInfo.getCpuRevision());
        log.info("CPU Architecture  :  " + SystemInfo.getCpuArchitecture());
        log.info("CPU Part          :  " + SystemInfo.getCpuPart());
        log.info("CPU Temperature   :  " + SystemInfo.getCpuTemperature());
        log.info("CPU Core Voltage  :  " + SystemInfo.getCpuVoltage());
        log.info("CPU Model Name    :  " + SystemInfo.getModelName());
        log.info("Processor         :  " + SystemInfo.getProcessor());
        log.info("Hardware Revision :  " + SystemInfo.getRevision());
        log.info("Is Hard Float ABI :  " + SystemInfo.isHardFloatAbi());
        log.info("Board Type        :  " + SystemInfo.getBoardType().name());

        log.info("----------------------------------------------------");
        log.info("MEMORY INFO");
        log.info("----------------------------------------------------");
        log.info("Total Memory      :  " + SystemInfo.getMemoryTotal());
        log.info("Used Memory       :  " + SystemInfo.getMemoryUsed());
        log.info("Free Memory       :  " + SystemInfo.getMemoryFree());
        log.info("Shared Memory     :  " + SystemInfo.getMemoryShared());
        log.info("Memory Buffers    :  " + SystemInfo.getMemoryBuffers());
        log.info("Cached Memory     :  " + SystemInfo.getMemoryCached());
        log.info("SDRAM_C Voltage   :  " + SystemInfo.getMemoryVoltageSDRam_C());
        log.info("SDRAM_I Voltage   :  " + SystemInfo.getMemoryVoltageSDRam_I());
        log.info("SDRAM_P Voltage   :  " + SystemInfo.getMemoryVoltageSDRam_P());

        log.info("----------------------------------------------------");
        log.info("OPERATING SYSTEM INFO");
        log.info("----------------------------------------------------");
        log.info("OS Name           :  " + SystemInfo.getOsName());
        log.info("OS Version        :  " + SystemInfo.getOsVersion());
        log.info("OS Architecture   :  " + SystemInfo.getOsArch());
        log.info("OS Firmware Build :  " + SystemInfo.getOsFirmwareBuild());
        log.info("OS Firmware Date  :  " + SystemInfo.getOsFirmwareDate());

        log.info("----------------------------------------------------");
        log.info("JAVA ENVIRONMENT INFO");
        log.info("----------------------------------------------------");
        log.info("Java Vendor       :  " + SystemInfo.getJavaVendor());
        log.info("Java Vendor URL   :  " + SystemInfo.getJavaVendorUrl());
        log.info("Java Version      :  " + SystemInfo.getJavaVersion());
        log.info("Java VM           :  " + SystemInfo.getJavaVirtualMachine());
        log.info("Java Runtime      :  " + SystemInfo.getJavaRuntime());

        log.info("----------------------------------------------------");
        log.info("NETWORK INFO");
        log.info("----------------------------------------------------");

        // display some of the network information
        log.info("Hostname          :  " + NetworkInfo.getHostname());
        for (String ipAddress : NetworkInfo.getIPAddresses())
            log.info("IP Addresses      :  " + ipAddress);
        for (String fqdn : NetworkInfo.getFQDNs())
            log.info("FQDN              :  " + fqdn);
        for (String nameserver : NetworkInfo.getNameservers())
            log.info("Nameserver        :  " + nameserver);

        log.info("----------------------------------------------------");
        log.info("CODEC INFO");
        log.info("----------------------------------------------------");
        log.info("H264 Codec Enabled:  " + SystemInfo.getCodecH264Enabled());
        log.info("MPG2 Codec Enabled:  " + SystemInfo.getCodecMPG2Enabled());
        log.info("WVC1 Codec Enabled:  " + SystemInfo.getCodecWVC1Enabled());

        log.info("----------------------------------------------------");
        log.info("CLOCK INFO");
        log.info("----------------------------------------------------");
        log.info("ARM Frequency     :  " + SystemInfo.getClockFrequencyArm());
        log.info("CORE Frequency    :  " + SystemInfo.getClockFrequencyCore());
        log.info("H264 Frequency    :  " + SystemInfo.getClockFrequencyH264());
        log.info("ISP Frequency     :  " + SystemInfo.getClockFrequencyISP());
        log.info("V3D Frequency     :  " + SystemInfo.getClockFrequencyV3D());
        log.info("UART Frequency    :  " + SystemInfo.getClockFrequencyUART());
        log.info("PWM Frequency     :  " + SystemInfo.getClockFrequencyPWM());
        log.info("EMMC Frequency    :  " + SystemInfo.getClockFrequencyEMMC());
        log.info("Pixel Frequency   :  " + SystemInfo.getClockFrequencyPixel());
        log.info("VEC Frequency     :  " + SystemInfo.getClockFrequencyVEC());
        log.info("HDMI Frequency    :  " + SystemInfo.getClockFrequencyHDMI());
        log.info("DPI Frequency     :  " + SystemInfo.getClockFrequencyDPI());
    }

    private void waitForQuit() {
        Console console = System.console();

        if (console != null) {
            console.format("\nPress Q to quit.\n");

            String input = console.readLine();

            log.info("entered: " + input);
        }
    }

    public static void main(String[] args) throws InterruptedException, IOException, ParseException {
        new App();
    }

}
