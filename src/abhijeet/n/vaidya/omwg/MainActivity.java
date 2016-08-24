package abhijeet.n.vaidya.omwg;

import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.res.AssetManager;
import android.view.View;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import android.os.CountDownTimer;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;

public class MainActivity extends Activity {

    private Stack<Integer> stk; // stores the ids of the letter-tile buttons pressed so far (in order)
    private Vector<String> letter_grid; // letters on the tiles (it's actually array of characters)
    private Vector<String> player_words;
    private int player_word_count;
    private Vector<String> computer_words;
    private int computer_word_count;
    private int computer_word_index;
    private Vector<String> long_words;
    private Vector<String> all_possible_words;
    private int winner;
    private Map<String, String> anagrams_vs_words = new HashMap<String, String>();
    private Map<String, String> word_definitions = new HashMap<String, String>();

    private int game_state;
    
    private int game_screen; // To handle "back button"
    private static int INTRO = 0;
    private static int GAMEPLAY = 1;
    private static int GAMEHELP = 2;
    
    private Timer no_activity_timeout;
    private Timer comp_turn_timer;
    private CountDownTimer timeout_warning;
    private StartUpDelay game_start_countdown;
    
    private Vector<String> sorted_letter_grid;
    private Set<String> all_letter_combinations;
    
    private static int TIMEOUT_INTERVAL = 30000;
    private static int COMP_TURN_DELAY = 7000;
    private static int STARTUP_DELAY = 11000;
    private static final int RACE_TO_WORDS = 10;
    private static final int NUM_OF_DEFINITIONS_TO_DISPLAY = 500;
    
    private long seconds_left_to_timeout = 0;
    private long mins_left_to_timeout = 0;
    private long sec_to_go = 0;
    
    private int free_play_glb = 0;       // global variables are assigned to these local ones so
    private int min_word_length_glb = 3; // that timer-class methods can access them.
    
    private String all_words_defs_html;
    
    class theLock extends Object {
    }
    public theLock lockObject = new theLock();

    class ComputerTurn extends TimerTask {
        @Override
        public void run() {
            runOnUiThread(new Runnable() {              
                @Override
                public void run() {
                    if (game_state == 1) {
                        if (computer_word_index < all_possible_words.size()) {
                            String comp_word = all_possible_words.elementAt(computer_word_index);
                            ++computer_word_index;
                            computer_words.addElement(comp_word);
                            UpdateWordCounts();

                            // Taunts
                            if (computer_word_count - player_word_count > 2)
                                UpdateCommentary("Hurry! I'm ahead by " + (computer_word_count - player_word_count), R.id.commentary);
                            else if (computer_word_count == (RACE_TO_WORDS-2))
                                UpdateCommentary("Hurry! I'm 2 away from victory!", R.id.commentary);
                            else if (player_words.contains(comp_word))
                                UpdateCommentary("I got \"" + comp_word + "\"", R.id.commentary);
                            
                            if (computer_word_count == RACE_TO_WORDS)
                                DecideWinner(0);
                        }
                    }
                } // Runnable.run
            });
        }
    }

    class Timeout extends TimerTask {
        @Override
        public void run() {
            runOnUiThread(new Runnable() {              
                @Override
                public void run() {
                    DecideWinner(1);
                }
            });
        }
    }
    
    class TimeoutWarning extends CountDownTimer {
        public TimeoutWarning(long millisInFuture, long countDownInterval) {
            super(millisInFuture, countDownInterval);
        }

        @Override
        public void onFinish() {
        }

        @Override
        public void onTick(long millisUntilFinished) {
            seconds_left_to_timeout = millisUntilFinished / 1000;
            mins_left_to_timeout = (seconds_left_to_timeout / 60);
            seconds_left_to_timeout -= (mins_left_to_timeout * 60);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    UpdateCommentary(String.format("%d:%02d", mins_left_to_timeout, seconds_left_to_timeout), R.id.countdown);
                }
            });
        }
    }
    
    class StartUpDelay extends CountDownTimer {
    	public StartUpDelay(long millisInFuture, long countDownInterval) {
    		super(millisInFuture, countDownInterval);
    	}
    	@Override
    	public void onFinish() {
    		game_state = 1;
          	String commentary = (free_play_glb == 1) ? "Free play! " : "Start! ";
  	        commentary += (all_possible_words.size() + " words possible!");
    	   UpdateCommentary(commentary, R.id.commentary);
      	}
    	@Override
    	public void onTick(long millisUntilFinished) {
			sec_to_go = millisUntilFinished / 1000;
    		runOnUiThread(new Runnable() {
    			@Override
    			public void run() {
    				UpdateCommentary(String.format("Get Ready! Game begins in %d",  sec_to_go), R.id.commentary);
    			}
    		});
    	}
    }
    
    
    class LongWordGenerator extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... arg0) {
        	Boolean cancelled = false;
        	int i;
        	for (i = 7; i < 16; ) {
                PrepareWordDatabase(i, i+1, 1); // Add it to the long words container for safety. (Because all_possible_words might be shuffling while we process this).
            	if (isCancelled()) {
            		cancelled = true;
            		break;
            	}
                i += 2;
        	}
        	if (i >= 16 || cancelled) {
            	Collections.shuffle(long_words);
            	all_possible_words.addAll(long_words);
            	long_words.clear();
            	
	            {    // This takes hell lot of time and should never run in the UI thread.
	            	 //	all_words_defs_html string can be constructed as soon as we have all_possible_words
	            	Vector<String> sorted_all_possible_words = new Vector<String>(all_possible_words);
	            	//Collections.sort(sorted_all_possible_words);
	                all_words_defs_html = new String("<html>\n<style type=\"text/css\">\n<!--\n body {\n text-align:left;\n color:white;\n background-color:black;\n}\n a {\n text-decoration:none;\n color:white;\n}\n-->\n</style>\n<body>\n");
	                for (int j = 0; j < sorted_all_possible_words.size() && j < NUM_OF_DEFINITIONS_TO_DISPLAY; ++j) {
	                	String w = sorted_all_possible_words.elementAt(j);
	            	    if (word_definitions.containsKey(w)) {
	            		    String meaning = word_definitions.get(w);
	            		    all_words_defs_html += ("<b>" + w + "</b> <span style=\"color:#ADD8E6;\"><i>" + meaning + "</i></span><br/>"); 
	            	    } else // hyperlink undefined words!
	            		    all_words_defs_html += ("<b><a href=\"https://www.google.co.in/search?q=define+" + w + "\">" + w + "</a></b><br/>");
	                }
	                if (sorted_all_possible_words.size() > NUM_OF_DEFINITIONS_TO_DISPLAY)
	                	all_words_defs_html += "... ... ...<br/>";
	                all_words_defs_html += "</body></html>";
	                sorted_all_possible_words.clear();
            	}
        	}
            return null;
        }
        protected void onPostExecute(Void v) { // Runs on the UI thread!
        	//UpdateCommentary(all_possible_words.size()+" words possible!", R.id.commentary);
        }
     }
    
     private LongWordGenerator lwg;
     
     class LongWordDefReader extends AsyncTask<Void, Void, Void> {
    	 		@Override
    	 protected Void doInBackground(Void... arg0) {  
    		 try {
    			ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
				if (/*false ==  am.isLowRamDevice() TargetApi(19)*/ am.getMemoryClass() >= 64) {
					ReadDefinitions("defs56.txt");
         		    ReadDefinitions("defs7.txt");
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
    		 return null;
    	 }
     }
     
     private LongWordDefReader lwdr;
     
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        stk = new Stack<Integer>();
        letter_grid = new Vector<String>();
        player_words = new Vector<String>();
        computer_words = new Vector<String>();
        long_words = new Vector<String>();
        all_possible_words = new Vector<String>();
        game_state = 0; // Off
        game_screen = INTRO;
        player_word_count = 0;
        computer_word_count = 0;
        computer_word_index = 0;
        winner = -1;
        all_words_defs_html = "";
        free_play_glb = 0;
        min_word_length_glb = 3;
        try {
            ReadDictionary();
            ReadDefinitions("defs34.txt");
            lwdr = new LongWordDefReader();
            lwdr.execute();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void DisplayMainMenu(View view) {
        setContentView(R.layout.activity_main);
        game_screen = INTRO;
    }

    public void DisplayHelp(View view) {
        setContentView(R.layout.help);
        WebView wview = (WebView)findViewById(R.id.help_text1);
        wview.loadUrl("file:///android_asset/omwg_help.html");
        game_screen = GAMEPLAY;
    }
    
    public void Play(View view) {
        setContentView(R.layout.game);
        stk.clear();
        letter_grid.clear();
        player_words.clear();
        computer_words.clear();
        long_words.clear();
        all_possible_words.clear();
        game_state = 0; // Off
        player_word_count = 0;
        computer_word_count = 0;
        computer_word_index = 0;
        winner = -1;
        min_word_length_glb = ((Globals) this.getApplication()).MIN_WORD_LENGTH_GLB;
        free_play_glb = ((Globals) this.getApplication()).FREE_PLAY_GLB;
        
        if (min_word_length_glb > 3) {
        	TIMEOUT_INTERVAL = 150000;
        	COMP_TURN_DELAY = 15000;
        } else {
        	TIMEOUT_INTERVAL = 30000;
        	COMP_TURN_DELAY = 7000;
        }
        { // Because webview initializes to a white background
        WebView wview = (WebView)findViewById(R.id.player_words);
        String empty_html = new String("<html><body style=\"text-align:center;color:white;background-color:black;\"></body></html>");
        wview.loadData(empty_html, "text/html", "utf-8");
        }
        CreateLetterGrid();
        PrepareWordDatabase(min_word_length_glb, 6, 0); // Generate words of lengths from MIN to 6
        Collections.shuffle(all_possible_words);
        lwg = new LongWordGenerator();
        lwg.execute(); // Generate lengths 7 to 16 in the background to make UI loading faster.
        game_state = 0;
        if (free_play_glb == 0) {
	        comp_turn_timer = new Timer();
	        comp_turn_timer.schedule(new ComputerTurn(), COMP_TURN_DELAY+STARTUP_DELAY, COMP_TURN_DELAY);   
	        no_activity_timeout = new Timer();
	        no_activity_timeout.schedule(new Timeout(), TIMEOUT_INTERVAL+STARTUP_DELAY);
	        timeout_warning = new TimeoutWarning(TIMEOUT_INTERVAL+STARTUP_DELAY, 1000);
	        timeout_warning.start();
	    }
        // This counter prints "Game begins in..." and buys time for building word definitions' html string!
        game_start_countdown = new StartUpDelay(STARTUP_DELAY, 1000);
        game_start_countdown.start();
        game_screen = GAMEPLAY;
    }
    
    public void LetterClicked(View view) {
    	if (game_state == 0) return;
        Button b = (Button)findViewById(view.getId());
        b.setVisibility(View.INVISIBLE);
        TextView word_view = (TextView)findViewById(R.id.word_edit);
        String word = word_view.getText().toString();
        word += b.getText().toString();
        word_view.setText(word);
        stk.push(new Integer(view.getId()));
     }
    
    public void TakeBack(View view) {
    	if (game_state == 0) return;
        TextView word_view = (TextView)findViewById(R.id.word_edit);
        String word = word_view.getText().toString();
        if (word.length() > 0) {
            Integer last_letter_id = stk.pop();
            Button b = (Button)findViewById(last_letter_id);
            b.setVisibility(View.VISIBLE);
            word = word.substring(0, word.length()-1);
            word_view.setText(word);
            UpdateCommentary("", R.id.commentary);
        }
    }
    
    public void CreateLetterGrid() {
        String alphabet = "AAABCDEEEFGHIIIJKLMNNNOOOPQRSTUUUVWXYYZ";
        Random random_letter_generator = new Random();
        for (int i = 0; i < 16; ++i) {
            int letter_id = random_letter_generator.nextInt(alphabet.length());
            String letter = new String(alphabet.charAt(letter_id) + "");
            letter_grid.addElement(letter);
            switch (i) {
                case 0 : {
                    Button b = (Button)findViewById(R.id.letter1);
                    b.setText(letter);
                    break;
                }
                case 1 : {
                    Button b = (Button)findViewById(R.id.letter2);
                    b.setText(letter);
                    break;
                }
                case 2 : {
                    Button b = (Button)findViewById(R.id.letter3);
                    b.setText(letter);
                    break;
                }
                case 3 : {
                    Button b = (Button)findViewById(R.id.letter4);
                    b.setText(letter);
                    break;
                }
                case 4 : {
                    Button b = (Button)findViewById(R.id.letter5);
                    b.setText(letter);
                    break;
                }
                case 5 : {
                    Button b = (Button)findViewById(R.id.letter6);
                    b.setText(letter);
                    break;
                }
                case 6 : {
                    Button b = (Button)findViewById(R.id.letter7);
                    b.setText(letter);
                    break;
                }
                case 7 : {
                    Button b = (Button)findViewById(R.id.letter8);
                    b.setText(letter);
                    break;
                }
                case 8 : {
                    Button b = (Button)findViewById(R.id.letter9);
                    b.setText(letter);
                    break;
                }
                case 9 : {
                    Button b = (Button)findViewById(R.id.letter10);
                    b.setText(letter);
                    break;
                }
                case 10 : {
                    Button b = (Button)findViewById(R.id.letter11);
                    b.setText(letter);
                    break;
                }
                case 11 : {
                    Button b = (Button)findViewById(R.id.letter12);
                    b.setText(letter);
                    break;
                }
                case 12 : {
                    Button b = (Button)findViewById(R.id.letter13);
                    b.setText(letter);
                    break;
                }
                case 13 : {
                    Button b = (Button)findViewById(R.id.letter14);
                    b.setText(letter);
                    break;
                }
                case 14 : {
                    Button b = (Button)findViewById(R.id.letter15);
                    b.setText(letter);
                    break;
                }
                case 15 : {
                    Button b = (Button)findViewById(R.id.letter16);
                    b.setText(letter);
                    break;
                }
            }
        }
    }

    public void RestoreLetterGrid(View view) {
    	if (game_state == 0) return;
        TextView word_view = (TextView)findViewById(R.id.word_edit);
        String word = word_view.getText().toString();
        if (word.length() > 0) {
            for (int i = 0; i < word.length(); ++i)
                TakeBack(view);
            UpdateCommentary("", R.id.commentary);
        }
    }
    
    public void EndWord(View view) {
    	if (game_state == 0) return;
        TextView word_view = (TextView)findViewById(R.id.word_edit);
        String word = word_view.getText().toString();
        if (word.length() == 16 && free_play_glb == 1) { // FIXME 
            // For free play mode we must provide a backdoor exit here since there is no "Go to main menu" button
        	game_state = 0;
        	lwg.cancel(true); // onPostExecute is not called now
        	//UpdateCommentary("Aborting...", R.id.commentary); // FIXME never displayed
        	DecideWinner(1); // abort the game
        } else if (word.length() > 0) {        	
            boolean already_present = player_words.contains(word);
            if (!already_present && all_possible_words.contains(word)) {
                player_words.addElement(new String(word));
                if (free_play_glb == 0) {
	                no_activity_timeout.cancel();
	                no_activity_timeout = new Timer();
	                no_activity_timeout.schedule(new Timeout(), TIMEOUT_INTERVAL);
	                timeout_warning.cancel();
	                timeout_warning = new TimeoutWarning(TIMEOUT_INTERVAL, 1000);
	                timeout_warning.start();
                }
                RestoreLetterGrid(view);
                UpdateWordCounts();
                WebView wview = (WebView)findViewById(R.id.player_words);
                String html = new String("<html><body style=\"text-align:center;color:white;background-color:black;font-size:1.5em;\">");
                for (int i = 0; i < player_words.size(); ++i)
                    if (computer_words.contains(player_words.elementAt(i)))
                        html += "<strike>" + (player_words.elementAt(i) + "</strike><br/>");
                    else
                        html += (player_words.elementAt(i) + "<br/>");
                html += "</body></html>";
                wview.loadData(html, "text/html", "utf-8");
                
                // Taunts
                if (word.length() == 5)
                	UpdateCommentary("Good! \"" + word + "\" accepted.", R.id.commentary);
                else if (word.length() > 5)
                	UpdateCommentary("Way to go!", R.id.commentary);
                else if (computer_words.contains(word))
                    UpdateCommentary("I got "+word+" too!", R.id.commentary);
                else if (computer_word_count > 1)
                	UpdateCommentary("I've made "+computer_word_count+" words.", R.id.commentary);
             
                if (player_word_count == RACE_TO_WORDS)
                    DecideWinner(0);
            } else if (already_present) {
                UpdateCommentary("You already formed \"" + word + "\"", R.id.commentary);
            } else { // Unknown word
            	if (word.length() < 4 && min_word_length_glb > 3)
            		UpdateCommentary("REJECTED! Min length must be "+min_word_length_glb, R.id.commentary);
            	else
                    UpdateCommentary("What is \"" + word + "\"?", R.id.commentary);
            }
        }
    }
    
    public void DecideWinner(int is_timeout) {
        synchronized (lockObject) {
            if (game_state == 1) {
                winner = -1; // draw
                if (player_word_count > computer_word_count)
                    winner = 1; // player
                else if (player_word_count < computer_word_count)
                    winner = 2; // computer
            }
            game_state = 0; // Game over
            if (free_play_glb == 0) {
                comp_turn_timer.cancel();
                no_activity_timeout.cancel();
                timeout_warning.cancel();
            }
            // FIXME - It is never displayed, may be subsequent setContentView takes precedence
            //UpdateCommentary("Game over.", R.id.commentary); // If the all-possible-words are too many it takes
                                                               // time to load up victory screen (due to sorting step)
            
            setContentView(R.layout.winner);

            TextView winner_view = (TextView)findViewById(R.id.winner);
            String result = (winner == 1) ? "You won!" : "Computer won";
            if (winner == -1) result = "Game drawn!";
            if (is_timeout == 1) result += " (Timeout)";
            if (((Globals) this.getApplication()).FREE_PLAY_GLB == 1) {
            	result = "Free play";
            	if (is_timeout == 1)
            		result += " (Abored)";
            }
            winner_view.setText(result);
            
            TextView letters_view = (TextView)findViewById(R.id.letters);
            String ls = "";
            for (int i = 0; i < letter_grid.size(); ++i)
                ls += (letter_grid.elementAt(i) + " ");
            letters_view.setText(ls);
 
            // Display player's words
            WebView wview0 = (WebView)findViewById(R.id.your_words);
            String html0 = new String("<html>\n<style type=\"text/css\">\n<!--\n body {\n text-align:left;\n color:white;\n background-color:black;\n}\n a {\n text-decoration:none;\n color:white;\n}\n-->\n</style>\n<body>\n");
            for (int i = 0; i < player_words.size(); ++i) {
            	String w = player_words.elementAt(i);
        	    if (word_definitions.containsKey(w)) {
        		    String meaning = word_definitions.get(w);
        		    html0 += ("<b>" + w + "</b> <span style=\"color:#ADD8E6;\"><i>" + meaning + "</i></span><br/>"); 
        	    } else
        		    html0 += ("<b><a href=\"https://www.google.co.in/search?q=define+" + w + "\">" + w + "</a></b><br/>");
            }
            html0 += "</body></html>";
            wview0.loadData(html0, "text/html", "utf-8");
/*
            // Display computer's words (Hyperlinked)
            WebView wview1 = (WebView)findViewById(R.id.comp_words);
            String html1 = new String("<html>\n<style type=\"text/css\">\n<!--\n body {\n text-align:left;\n color:white;\n background-color:black;\n}\n a {\n text-decoration:none;\n color:white;\n}\n-->\n</style>\n<body>\n");
            for (int i = 0; i < computer_words.size(); ++i) {
            	String w = computer_words.elementAt(i);
            	html1 += ("<b><a href=\"https://www.google.co.in/search?q=define+" + w + "\">" + w + "</a></b><br/>");
            }
            html1 += "</body></html>";
            wview1.loadData(html1, "text/html", "utf-8");
*/
            // Display all the possible words with their definitions if found
            TextView all_words_disp_title = (TextView)findViewById(R.id.all_words_title);
            all_words_disp_title.setText("Possible words (Hyperlinked) ("+all_possible_words.size()+")");            
            WebView wview2 = (WebView)findViewById(R.id.all_words);
            wview2.loadData(all_words_defs_html, "text/html", "utf-8"); // html content string is populated inside LongWordGenerator.doInBackground async task
        }
    }
    
    public void PrepareWordDatabase(int from_len, int to_len, int add_to) {
        sorted_letter_grid = new Vector<String>(letter_grid);
        Collections.sort(sorted_letter_grid);
        all_letter_combinations = new HashSet<String>();
        int[] v = new int[16];
        for (int l = from_len; l <= to_len; ++l)
            Combinations(v, 0, 16, 0, l);
        Iterator<String> i = all_letter_combinations.iterator();
        while (i.hasNext()) {
            String anagram = i.next();
            if (anagrams_vs_words.containsKey(anagram)) {
                String[] words = anagrams_vs_words.get(anagram).split(" ");    
                for (String w : words)
                	if (add_to == 0)
                        all_possible_words.addElement(w);
                	else
                		long_words.addElement(w);
            }
        }
        all_letter_combinations.clear();
        sorted_letter_grid.clear();
    }
 
    public void ReadDictionary() throws IOException {
        if (anagrams_vs_words.size() > 0) // Already read
            return;
        AssetManager asset_mgr = this.getAssets();
        InputStream anag_dict_stream = asset_mgr.open("anagrams_dict.txt");     
        InputStreamReader inputStreamReader = new InputStreamReader(anag_dict_stream);
        BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
        String line = "";
        String anagram = "";
        int i = 1;
        while ((line = bufferedReader.readLine()) != null) {
            if (i % 2 == 0)
                anagrams_vs_words.put(anagram, line);
            else
                anagram = line;
            ++i;
        }
        anag_dict_stream.close();
    }
    
    public void ReadDefinitions(String word_definition_file) throws IOException {
    	AssetManager asset_mgr = this.getAssets();
    	InputStream defs_stream = asset_mgr.open(word_definition_file);
    	InputStreamReader inputStreamReader = new InputStreamReader(defs_stream);
    	BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
    	String line = "";
    	String word = "";
    	int i = 1;
    	while ((line = bufferedReader.readLine()) != null) {
    		if (i % 2 == 0)
    			word_definitions.put(word, line);
    		else
    			word = line; 
    		++i;
    	}
    	defs_stream.close();
    }
    
    public void UpdateCommentary(String c, int view_id) {
        TextView commentary = (TextView)findViewById(view_id);
        commentary.setText(c);    
    }
    
    public void Combinations(int[] v, int start, int n, int k, int maxk) {
        if (k >= maxk) {
            String comb = "";
            for (int i = 0; i < maxk; ++i)
                comb += sorted_letter_grid.elementAt(v[i]);
            all_letter_combinations.add(comb);
            return;
        }
        // for this k'th element of the v, try all start... n elements in that position
        for (int i = start; i < n; ++i) {
            v[k] = i;
            // recursively generate combinations of integers from i+1... n
            Combinations(v, i+1, n, k+1, maxk);
        }
    }
    
    public void UpdateWordCounts() {
        synchronized (lockObject) {
            player_word_count = 0;
            for (int i = 0; i < player_words.size(); ++i)
                if (!computer_words.contains(player_words.elementAt(i)))
                    ++player_word_count;
            computer_word_count = 0;
            for (int i = 0; i < computer_words.size(); ++i)
                if (!player_words.contains(computer_words.elementAt(i)))
                    ++computer_word_count;
        }
    }

    @Override
    public void onBackPressed() {
    	game_state = 0;
        lwdr.cancel(true);
    	if (game_screen == INTRO)
    	    finish();
    	else if (game_screen == GAMEPLAY) {
    		game_start_countdown.cancel();
            if (free_play_glb == 0) {
                comp_turn_timer.cancel();
                no_activity_timeout.cancel();
                timeout_warning.cancel();
            }
            lwg.cancel(true);
    		DisplayMainMenu(findViewById(R.layout.activity_main));
    	} else if (game_screen == GAMEHELP)
    		DisplayMainMenu(findViewById(R.layout.activity_main));
    }

    public void ShowOptions(View view) {
    	setContentView(R.layout.options);
    	CheckBox wlchkbox = (CheckBox)findViewById(R.id.word_length_chkbox);
    	if (((Globals) this.getApplication()).MIN_WORD_LENGTH_GLB == 3)
    		wlchkbox.setChecked(true);
    	else
    		wlchkbox.setChecked(false);
    	CheckBox freeplay_chkbox = (CheckBox)findViewById(R.id.free_play_chkbox);
    	if (((Globals) this.getApplication()).FREE_PLAY_GLB == 1)
    		freeplay_chkbox.setChecked(true);
    	else
    		freeplay_chkbox.setChecked(false);
     }
   
    public void Allow3LetterWordOpt(View view) {
        boolean on = ((CheckBox) view).isChecked();
        ((Globals) this.getApplication()).MIN_WORD_LENGTH_GLB = on ? 3 : 4;
    }
    
    public void AllowFreePlay(View view) {
    	boolean on = ((CheckBox) view).isChecked();
    	((Globals) this.getApplication()).FREE_PLAY_GLB = on ? 1 : 0;
    }
}
