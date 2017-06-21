// Uses Java threads, to simulate the movement of people between branches using the taxi.
// Use semaphores to synchronize the taxi thread and the people threads.

import java.util.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.concurrent.Semaphore;
import java.util.Random;

public class Simulator extends Thread
{
    private static Person [] people;
    private static Taxi taxi;
    protected static Semaphore sem_Taxi   = new Semaphore(1);
    protected static Semaphore sem_taxiQueues= new Semaphore(1);

    private String filename ;

    public Simulator(String filename) {
        this.filename = filename ;
        initializeData() ;
    }

    @Override
    public void run()
    {
        // Get taxi ready for people to hail and request branches.
        taxi.start();

        // Get People ready to hail , request branches, travel, and wait in branches.
        for  (int p=0; p<people.length; p++)
        {
            try {
                people[p].start();
                // wait for a max of 33 ms (=1min) random time
                try { sleep( new Random().nextInt((3300/100)) );  } catch(InterruptedException ex) {}
            }
            catch (java.lang.NullPointerException ec) { }
        }
    }

    public void initializeData() {
        // Read the from a file:
        int n_people ; //<newline>
        int n_branches ; //<newline>

        try
        {
            // <person number> (<branch, duration>), ( <branch, duration>), ....  <newline>
            File xfile = new File(filename);
            Scanner fline = new Scanner(xfile);

            n_people = fline.nextInt();
            n_branches = fline.nextInt();

            taxi = new Taxi(n_branches); //, people);
            people = new Person[n_people];

            int line_count=0;

            while (fline.hasNextInt() && line_count < n_people)
            {
                int person_i =  fline.nextInt();

                ArrayList<Integer> branches = new ArrayList<>();
                ArrayList<Long>   durations = new ArrayList<>();

                String[] personData = fline.nextLine().split(",");
                int destinations = personData.length;

                for (int i = 0; i<destinations/2; i++)
                {
                    // Branches are even indexes --  0,2,4,6... 2*i
                    int branch = new Scanner(personData[i * 2]).useDelimiter("\\D+").nextInt();
                    // Duration is order indexes --  1,3,5,7... 2*i+1
                    long duration = new Scanner(personData[i * 2 + 1]).useDelimiter("\\D+").nextInt();

                    branches.add(branch);
                    durations.add(duration);
                }
                Person newPerson = new Person(person_i, taxi, branches, durations );
                people[ line_count ] = newPerson;
                line_count++;
                System.out.println("p: ( " + people[ person_i ].getID() + ", b: " + branches + ", d: " + durations + " )");
            }
        }
        catch (FileNotFoundException fex) {
            System.out.println("file '"+filename+"' not found!");
        }
        catch (Exception ex) {
            ex.printStackTrace();
            ex.toString();
        }
    }

    public static void main (String [] args)
    {
        if (args.length > 0) {
            // Start running this Simulators's Thread
            Simulator sim = new Simulator( args[0] );
            sim.start();
        }
        else {
            System.out.println("Missing argument, to run simulator use: 'java Simulator filename'");
        }
    }

}