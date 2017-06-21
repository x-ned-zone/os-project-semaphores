import org.omg.Messaging.SyncScopeHelper;

import java.util.ArrayList;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.Lock;
import java.util.Random;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Person Class.
 * <p>
 * Each Person[i] thread, acquires Taxi to hail and request destination branches to travel to.
 * Taxi thread keeps looping to picks up, discharge and transport Person[i] to indicated branch and sets wait duration for Person[i] in that branch
 *
 * @author Masixole Ntshinga
 * @version 10/06/2017
 */

public class Person extends Thread
{
    private static Taxi taxi ;
    private PersonState state;
    protected enum PersonState { WAITING, HAILED, PICKEDUP, REQUESTED, TRAVELING }

    private int id;
    private int currentBranch;

    private ArrayList<Integer> branches = new ArrayList<>();
    private ArrayList<Long>   durations = new ArrayList<>();

    private Semaphore sem_Taxi = Simulator.sem_Taxi;
    private Lock mutexLock ;

    Person (int id, Taxi taxi, ArrayList<Integer> branches, ArrayList<Long> durations)
    {
        this.id = id ;
        this.taxi = taxi ;
        this.branches = branches ;
        this.durations = durations ;
        this.currentBranch = 0 ; // headquarters = 0
        this.state = PersonState.WAITING;
        mutexLock = new ReentrantLock();
    }

    public String toString() {
        return "Person "+ getID()+ " ( branches: "+ branches+",  destinations: "+durations+" )";
    }

    @Override
    public void run()
    {
        try {
            //for (int b = 0; b < branches.size(); b++)
            while(!branches.isEmpty())
            {
                // Hail only when waiting.
                if (state==PersonState.WAITING)
                    this.hailTaxi();

                // Request only when picked up.
                else if (state==PersonState.PICKEDUP)
                    this.requestBranch();

                int w_timeUnits = (3300/100); //
                sleep( new Random().nextInt ( w_timeUnits  ));  // wait for random max of 33 ms (1min) or less
            }

        }
        catch (Exception e) {
            e.printStackTrace();
            System.out.println(e);
        }
    }

    public int  getID()          { return id; }
    public void nextBranch()      { assert (!branches.isEmpty()); branches.remove(0); }
    public void nextDuration()     { assert (!durations.isEmpty()); durations.remove(0);}
    public int  getDestineBranch()  { assert (!branches.isEmpty()); return branches.get(0); }
    public long getDestineDuration() { assert (!durations.isEmpty()); return durations.get(0); }
    public int  getCurrentBranch() { return currentBranch; }
    public void setCurrentBranch(int b ){ currentBranch = b; }

    public synchronized void setState(PersonState state) throws InterruptedException
    {
        this.state = state;
    }

    public PersonState getPersonState() { return state;}

    public void hailTaxi() throws Exception {
        mutexLock.lock();
        sem_Taxi.acquire();
        taxi.hailTaxi(this);
        mutexLock.unlock();
    }

    public void requestBranch() throws Exception {
        mutexLock.lock();
        sem_Taxi.acquire();
        taxi.requestBranch(this);
        mutexLock.unlock();
    }

    public synchronized void moveWithTaxi (int branch) throws InterruptedException {
        mutexLock.lock();
        state = PersonState.TRAVELING;
        //System.out.println("Move ("+this+")");
        setCurrentBranch( branch );
        mutexLock.unlock();
    }

    public synchronized void dischargePerson (long durationInBranch) throws InterruptedException {
        mutexLock.lock();
        state = PersonState.WAITING;
        nextBranch();
        nextDuration();
        synchronized(this) {
            this.waitTime(durationInBranch);
        }
        mutexLock.unlock();
    }

    public void waitTime(long time) throws InterruptedException {
        try { sleep (time ); }
        catch (InterruptedException e) { e.printStackTrace(); }
    }
}
