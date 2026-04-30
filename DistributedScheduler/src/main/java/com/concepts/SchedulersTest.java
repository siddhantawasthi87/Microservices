package com.concepts;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalTime;

@Component
public class SchedulersTest {

    //fixedRate
    @Scheduled(initialDelay = 3000, fixedRate = 5000)
    public void myJobFixedRate() {
        System.out.println("Start: " + LocalTime.now());
    }

    //fixedDelay
    @Scheduled(initialDelay = 1000, fixedDelay = 5000)
    public void myJobFixedDelay() {

        LocalTime startTime =  LocalTime.now();

        try{
            Thread.sleep(3000);
        }catch (Exception e){
            e.printStackTrace();
        }

        LocalTime endTime =  LocalTime.now();
        System.out.println("StartedAt: " + startTime + " finishedAt: " + endTime);
    }

    //One time execution, initial delay is mandatory
    @Scheduled(initialDelay = 0)
    public void myJobOneTimeRun() {
        System.out.println("Start: " + LocalTime.now());
    }

    //cron job
    @Scheduled(cron = "0/3 * * * * *")
    public void myCronJob() {
        LocalTime startTime =  LocalTime.now();
        System.out.println("Start: " + startTime);
    }
}
