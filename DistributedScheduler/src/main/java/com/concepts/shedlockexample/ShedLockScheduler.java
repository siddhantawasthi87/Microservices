package com.concepts.shedlockexample;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ShedLockScheduler {

    @Scheduled(fixedRate = 60000) //1min
    @SchedulerLock(name = "myJob", lockAtLeastFor= "4m", lockAtMostFor = "8m")
    public void myJob() {
        try{
            Thread.sleep(30000);
        }catch (Exception e){
            e.printStackTrace();
        }
        System.out.println(" job execution started");
    }
}
