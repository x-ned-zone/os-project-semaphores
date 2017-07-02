import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Taxi Class.
 * <p>
 * This class runs a single thread (signifying a single taxi) that picks up people (threads) from headquarters and moves them to their
 * indicated branches and again runs picking and discharging people to their indicated branches
 *
 * @author Masixole Ntshinga
 * @version 10/06/2017
 */

public class Taxi extends Thread
{
    private enum Direction { INBOUND, OUTBOUND }  // Forward = Outbound, Background = Inbound
    private enum TaxiState { WAITING, READY, RUNNING }
    private TaxiState taxiState;
    private Direction direction;
    private Semaphore sem_Taxi   = Simulator.sem_Taxi;
    private Semaphore sem_taxiQueues = Simulator.sem_taxiQueues;

    private Lock mutexLock ; // used to prevent process starvation and deadlock

    private final int numberOfBranches;
    private int currentBranch;
    private int previousBranch;
    private int lastBranch;

    // Simulated time: 33ms = 1m real time
    // 9:00 + 9:(1)33/33 + 9:(2)33/33 + ... h : t/33
    private long startTime = 9;
    private long currentTime = startTime;
    private long hours = startTime;
    private long minutes = 0;
    private long pickUp_DischargeTime = 33 ;

    // data structures to store people who -> hail, pickedUp, and travel requested destination
    private Deque<Person> hailQueue = new ArrayDeque<>(); // waiting to be pickedUp
    private Deque<Person> pickedUpQueue = new ArrayDeque<>(); // picked, not yet requested
    private HashMap<Integer, Person> requestQueue = new HashMap<>(); // moving passengers

    // TRACE Events identifiers
    private final int HAIL   = 1;
    private final int REQUEST= 2;
    private final int DEPART = 3;
    private final int ARRIVE = 4;
    private final int PICKUP = 5;
    private final int DISEMBARK = 6;

    private int thisBranchrequests = 0;
    private int thisBranchPicked = 0;

    public Taxi (int numberOfBranches) {
        this.numberOfBranches = numberOfBranches;
        this.currentBranch =0;
        this.previousBranch=0;
        this.mutexLock = new ReentrantLock();
    }

    public String toString() {
        return "Taxi 1" + "( Branch: "+ currentBranch + ",  State: " + taxiState + ", Direction: "+direction+" )";
    }

    @Override
    public void run ()
    {
        //System.out.println("Taxi : " + sem_Taxi.availablePermits() );
        direction = Direction.OUTBOUND;
        taxiState = TaxiState.WAITING;

        while(true)
        {
            try {
                // Move between branches
                while ( !hailQueue.isEmpty() || !pickedUpQueue.isEmpty() || !requestQueue.isEmpty() ) {
                    pickUp();
                    discharge();
                    moveToNextBranch();

                    // tick 1 second in the clock
                    sleep( 33); // wait
                }
            }
            catch (Exception e) {
                e.printStackTrace();
                System.out.println(e);
            }
        }
    }

    // Taxi takes 1 minute to pick up passengers at a branch.
    public void pickUp () throws InterruptedException
    {
        mutexLock.lock();
        this.waitTime(pickUp_DischargeTime);

        if (!hailQueue.isEmpty() && (taxiState == TaxiState.WAITING))
        {
            synchronized (hailQueue)
            {
                //System.out.println("Picking Up ... hailQ = "+hailQueue.size());
                // get person data
                sem_taxiQueues.acquire();
                for (Person person_i: hailQueue)
                {
                    if ((person_i.getPersonState() == Person.PersonState.HAILED) && (person_i.getCurrentBranch() == currentBranch))
                    {
                        person_i.setState(Person.PersonState.PICKEDUP);
                        pickedUpQueue.add(person_i);
                        hailQueue.remove(person_i);
                        TRACE(PICKUP,person_i);
                        thisBranchPicked++;
                    }
                }
                sem_taxiQueues.release();
            }
        }
        mutexLock.unlock();
    }

    // Taxi takes 1 minute to /discharge passengers at a branch.
    public void discharge () throws InterruptedException
    {
        mutexLock.lock();
        if (requestQueue.size()>0 && (previousBranch != currentBranch) && (taxiState == TaxiState.WAITING))
        {
            synchronized (pickedUpQueue)
            {
                // remove people from pickedUp queue
                sem_taxiQueues.acquire();
                for (Person pPicked: pickedUpQueue)
                {
                    assert (pPicked != null);
                    if (requestQueue.containsValue(pPicked))
                    {
                        Person pPerson = requestQueue.get(pPicked.getID());
                        int destineBranch = pPerson.getDestineBranch();
                        long waitDuration = pPerson.getDestineDuration();

                        // remove people with destinationBranch == currentBranch
                        if ( (destineBranch==currentBranch) && (pPerson.getPersonState()==Person.PersonState.TRAVELING))
                        {
                            pPerson.dischargePerson(waitDuration/33);
                            pickedUpQueue.remove(pPicked);
                            requestQueue.remove(pPerson.getID());
                            TRACE(DISEMBARK,pPerson);
                        }
                    }
                }
                sem_taxiQueues.release();
            }
            //System.out.println("Discharging DONE!!!");
        }
        taxiState = TaxiState.READY;
        mutexLock.unlock();
    }

    /**
     * Move Taxi (with people) from current branch to next branch
     * <p>
     * Taxi takes 2 minutes to move from one branch to the next.
     */

    public void moveToNextBranch () throws InterruptedException
    {
        int headquarters = 0;
        long travelingTime = 33*2;
        this.lastBranch = numberOfBranches-1;

        mutexLock.lock();
        if ( (thisBranchrequests==thisBranchPicked) &&
           ( (!hailQueue.isEmpty()) || (!requestQueue.isEmpty() && (taxiState == TaxiState.READY)) ) )
        {
            this.waitTime(travelingTime);
            TRACE(DEPART);
            taxiState = TaxiState.RUNNING;

            previousBranch = currentBranch;

            if (this.direction == Direction.OUTBOUND)
            {
                if (currentBranch < lastBranch)
                    currentBranch++;
                else if (currentBranch == lastBranch) {
                    currentBranch--;
                    this.direction = Direction.INBOUND;
                }
            }
            else {
                if (currentBranch > headquarters)
                    currentBranch--;
                else if (currentBranch == headquarters) {
                    currentBranch++;
                    this.direction = Direction.OUTBOUND;
                }
            }
            Iterator<Person> itr = requestQueue.values().iterator();

            while ( itr.hasNext() )
            {
                // Get person from queue
                Person pPerson = requestQueue.get(itr.next().getID());

                if ( pPerson.getPersonState()== Person.PersonState.REQUESTED )
                    pPerson.moveWithTaxi(currentBranch);
            }
            TRACE(ARRIVE);
            thisBranchrequests=0;
            thisBranchPicked=0;
            taxiState = TaxiState.WAITING;
        }
        else {
            taxiState = TaxiState.WAITING;
        }
        mutexLock.unlock();
    }

    public synchronized void hailTaxi(Person person) throws InterruptedException
    {
        mutexLock.lock();
        synchronized (hailQueue) {
            sem_taxiQueues.acquire();
            TRACE(HAIL, person);
            hailQueue.add(person);
            person.setState(Person.PersonState.HAILED);
            sem_taxiQueues.release();
            sem_Taxi.release();
        }
        mutexLock.unlock();
    }

    public synchronized void requestBranch(Person person) throws InterruptedException
    {
        mutexLock.lock();
        synchronized(requestQueue)
        {
            if (pickedUpQueue.contains(person))
            {
                sem_taxiQueues.acquire();
                TRACE(REQUEST, person);
                requestQueue.put(person.getID(), person);
                person.setState(Person.PersonState.REQUESTED);
                thisBranchrequests++;
                sem_taxiQueues.release();
                sem_Taxi.release();
            }
        }
        mutexLock.unlock();
    }

    public String simulateClock(long t)
    {
        minutes += t/33 ;
        if (minutes>=60) {
            hours += 1;
            if (hours>=24) {
                hours=0;
            }
            minutes = 0;
        }
        currentTime +=33;
        return hours +":"+ minutes;
    }

    /**
     * Sleep Taxi for t-time units
     * <p>
     *  One minute of simulated time is equal to 33 milliseconds of real time
     */
    public void waitTime(long time)
    {
        try { sleep (time ); }
        catch (InterruptedException e) { e.printStackTrace(); }
    }

    /**
     * Trace Taxi & Person events
     * <p>
     * <event> = hail | request <destination branch> | disembark | arrive | depart
     */

    public void TRACE (int number, Object... varargs)
    {
        String event;
        switch (number) {
            case HAIL:
                int id = ((Person)varargs[0]).getID();
                int current_branch = ((Person)varargs[0]).getCurrentBranch();
                event = String.format("%s branch %d : person %d hail", simulateClock(currentTime), current_branch,id);
                break;
            case REQUEST:
                int id2 = ((Person)varargs[0]).getID();
                int current_branch2 = ((Person)varargs[0]).getCurrentBranch();
                int destine_branch2 = ((Person)varargs[0]).getDestineBranch();
                event = String.format("%s branch %d : person %d request %d", simulateClock(currentTime), current_branch2, id2, destine_branch2);
                break;
            case DEPART:
                event = String.format("%s branch %d : taxi depart", simulateClock(currentTime), currentBranch );
                break;
            case ARRIVE:
                event = String.format("%s branch %d : taxi arrive ", simulateClock(currentTime), currentBranch );
                break;
            case PICKUP:
                event = String.format("%s branch %d : PICKEDUP person %d", simulateClock(currentTime), currentBranch, ((Person)varargs[0]).getID() );
                break;
            case DISEMBARK:
                event = String.format("%s branch %d : DISEMBARK person %d", simulateClock(currentTime), currentBranch, ((Person)varargs[0]).getID() );
                break;

            default:
                event = "ERROR UNKNOWN EVENT!";
        }
        System.out.printf(event+"\n");
    }
}
