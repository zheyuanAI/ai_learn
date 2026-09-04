package com.ailearn.platform.iot.telemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ailearn.platform.iot.telemetry.application.OfflineDeviceScanner;
import com.ailearn.platform.iot.telemetry.domain.port.DeviceStatusPort;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class OfflineDeviceScannerTest {

    @Test
    void scanDelegatesToStatusPortWithoutChangingLegacyConstructors() {
        DeviceStatusPort statusPort = Mockito.mock(DeviceStatusPort.class);
        when(statusPort.markOfflineIfTimedOut(org.mockito.ArgumentMatchers.any(OffsetDateTime.class))).thenReturn(2);

        assertEquals(2, new OfflineDeviceScanner(statusPort).scanNow());

        verify(statusPort).markOfflineIfTimedOut(org.mockito.ArgumentMatchers.any(OffsetDateTime.class));
    }
}
