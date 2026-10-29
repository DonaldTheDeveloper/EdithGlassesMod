package com.example.chattranslator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Offline "dialect" rewriter for English text. Not a real translation: it swaps words and phrases,
 * bends spelling toward an accent, and sometimes tacks on a typical sentence ending.
 * Entries are written "word=option|option|option"; one option is picked at random.
 */
public final class Dialect {
    private Dialect() {}

    public enum Mode {
        BRITISH("british", "British"),
        COCKNEY("cockney", "Cockney"),
        POSH("posh", "Posh (Queen's English)"),
        SCOTTISH("scottish", "Scottish"),
        NORTHERN("northern", "Northern (Yorkshire)"),
        WELSH("welsh", "Welsh English"),
        ROUXLS("rouxls", "Rouxls Kaard (Duke of Puzzles)");

        public final String code;
        public final String label;
        private Pattern pattern;
        private Map<String, String[]> words;
        private String[] tails;
        private boolean dropG;

        Mode(String code, String label) { this.code = code; this.label = label; }

        public static Mode byCode(String code) {
            if (code == null) return null;
            String c = code.toLowerCase(Locale.ROOT);
            switch (c) {
                case "brit", "en-gb", "uk" -> c = "british";
                case "scots", "scotland" -> c = "scottish";
                case "yorkshire", "yorks" -> c = "northern";
                case "wales" -> c = "welsh";
                case "kaard", "rouxls-kaard", "duke" -> c = "rouxls";
                default -> { }
            }
            for (Mode m : values()) if (m.code.equals(c)) return m;
            return null;
        }
    }

    public static String labelFor(String code) {
        Mode m = Mode.byCode(code);
        return m != null ? m.label : code;
    }

    // ------------------------------------------------------------------ dictionaries

    private static final String[] SPELLING = {
        "color=colour", "colors=colours", "colored=coloured", "favorite=favourite", "favorites=favourites",
        "honor=honour", "neighbor=neighbour", "neighbors=neighbours", "armor=armour", "flavor=flavour",
        "humor=humour", "labor=labour", "center=centre", "centers=centres", "defense=defence",
        "offense=offence", "license=licence", "gray=grey", "theater=theatre", "meter=metre", "meters=metres",
        "liter=litre", "realize=realise", "realized=realised", "organize=organise", "organized=organised",
        "recognize=recognise", "apologize=apologise", "summarize=summarise", "analyze=analyse",
        "traveled=travelled", "traveling=travelling", "jewelry=jewellery", "catalog=catalogue",
        "aluminum=aluminium", "mom=mum", "moms=mums", "mommy=mummy", "math=maths", "skeptical=sceptical",
        "pajamas=pyjamas", "plow=plough", "donut=doughnut", "mold=mould", "fiber=fibre", "behavior=behaviour",
        "enchanted=enchanted", "cozy=cosy", "program=programme", "practice=practise"
    };

    private static final String[] BRITISH = {
        // greetings and farewells
        "hello=alright|'ello|hiya|oi oi", "hi=hiya|alright|'ello", "hey=oi|hiya|alright",
        "goodbye=cheerio|ta-ra|toodle-oo|cheers then", "bye=ta-ra|cheerio|cheers|laters",
        "see you=see ya", "see you later=see you in a bit|catch you later|ta-ra for now",
        "good morning=morning|mornin'|top of the morning", "good night=night night|nighty-night|g'night",
        "good luck=best of luck|good luck, mate|break a leg",
        "thanks=cheers|ta|ta very much", "thank you=cheers|ta|cheers, ta", "thx=ta", "ty=ta",
        "sorry=ever so sorry|sorry about that|sorry", "excuse me=pardon me|'scuse me",
        "what's up=you alright|alright|how do", "whats up=you alright|alright", "wassup=alright", "sup=alright",
        "how are you=you alright|how're you keeping|how do you do",
        "how's it going=how's it going, mate|you alright|how's tricks",
        // people
        "friend=mate|mucker|pal", "friends=mates|muckers", "buddy=mate|mucker", "dude=mate|geezer", "bro=bruv|mate",
        "guys=lads|you lot", "guy=bloke|geezer|chap", "man=bloke|chap", "men=blokes|chaps", "boy=lad",
        "boys=lads", "girl=lass", "girls=lasses", "kid=nipper|sprog", "kids=nippers|sprogs", "child=nipper",
        "children=nippers", "everyone=everyone|all you lot", "everybody=everybody|all you lot", "you all=you lot",
        "y'all=you lot", "you guys=you lot",
        // praise
        "awesome=brilliant|smashing|cracking|ace", "amazing=brilliant|smashing|cracking", "cool=ace|brilliant|wicked|cracking",
        "great=brilliant|smashing|cracking", "nice=lovely|smashing|nice one", "good=decent|proper good|good",
        "fantastic=spot on|brilliant|top notch", "excellent=top notch|spot on|cracking", "perfect=spot on|perfect",
        "wonderful=lovely|smashing", "delicious=scrummy|lush|delicious", "yummy=scrummy|lush",
        // insults and moods
        "bad=rubbish|naff|dodgy", "terrible=dreadful|shocking|rubbish", "awful=dreadful|shocking",
        "horrible=ghastly|vile|rank", "disgusting=minging|rank|grim", "boring=dull as dishwater|tedious",
        "weird=peculiar|dodgy|odd", "strange=peculiar|odd", "crazy=barmy|bonkers|mental", "insane=barmy|bonkers|mental",
        "stupid=daft|barmy|thick", "idiot=muppet|numpty|plonker|wally", "idiots=muppets|plonkers|numpties",
        "dumb=daft|thick", "fool=prat|wally", "noob=numpty", "tired=knackered|shattered|cream crackered",
        "exhausted=knackered|absolutely shattered", "drunk=plastered|hammered|sloshed", "sad=gutted|down in the dumps",
        "disappointed=gutted", "upset=gutted", "angry=fuming|livid|cross", "annoyed=narked|cheesed off|miffed",
        "annoying=irritating|a right nuisance", "hungry=peckish|starving", "starving=famished",
        // intensifiers
        "very=proper|bloody|well|dead|very", "really=properly|bloody|well|really", "extremely=ever so|absolutely",
        "super=proper|ever so", "definitely=defo|definitely|for sure", "seriously=honestly|seriously",
        // money, places, things
        "money=quid|dosh|wonga|cash", "cash=dosh|wonga", "dollar=quid", "dollars=quid", "bucks=quid",
        "rich=minted|loaded", "broke=skint|boracic", "poor=skint", "toilet=loo|bog|khazi", "bathroom=loo|toilet",
        "restroom=loo", "elevator=lift", "apartment=flat", "truck=lorry", "soccer=football", "fries=chips",
        "chips=crisps", "candy=sweets", "cookie=biscuit", "cookies=biscuits", "pants=trousers", "sneakers=trainers",
        "sweater=jumper", "diaper=nappy", "trash=rubbish|bin", "garbage=rubbish", "vacation=holiday", "gas=petrol",
        "flashlight=torch", "movie=film", "movies=films", "cellphone=mobile", "cell phone=mobile", "phone=mobile|phone",
        "subway=underground|tube", "sidewalk=pavement", "faucet=tap", "soda=fizzy drink", "stroller=pushchair",
        "parking lot=car park", "mailman=postman", "mail=post", "eraser=rubber", "band-aid=plaster",
        "house=gaff|house", "party=knees-up|do|party", "beer=pint|bevvy|ale", "bar=pub", "tea=cuppa|brew",
        "sandwich=sarnie", "breakfast=brekkie", "food=grub|nosh", "snack=nibbles", "base=gaff|base",
        // law and order
        "police=the old bill|bobbies|rozzers", "cops=rozzers|bobbies", "cop=copper|bobby", "jail=nick|clink",
        "prison=clink|the nick", "steal=nick|pinch", "stole=nicked|pinched", "stolen=nicked|pinched",
        "thief=tea leaf", "arrest=nick", "arrested=nicked",
        // reactions
        "yes=yeah|yes mate|yep|aye", "yeah=yeah|aye|yep", "yep=yeah|aye", "nope=nah", "nah=nah",
        "okay=alright|right then|fair enough|righto", "ok=alright|right|fair enough|righto",
        "omg=blimey|crikey|bloody hell", "oh my god=blimey|crikey|bloody hell|cor blimey", "oh my gosh=blimey|crikey",
        "wow=blimey|cor|crikey", "damn=blast|bollocks|drat", "dang=blast|drat", "crap=pants|rubbish", "shoot=blast",
        "lol=hah|lol",
        // phrases
        "i don't know=I dunno|no idea mate|haven't a clue", "idk=dunno", "want to=wanna",
        "kind of=sort of", "a lot of=loads of", "lots of=loads of|heaps of", "a lot=loads|heaps", "i think=I reckon|I s'pose",
        "i guess=I s'pose|I reckon", "probably=prob'ly|most likely", "maybe=maybe|perhaps|mebbe",
        "wait=hang on|hold up|wait a tick", "hold on=hang on|hold your horses", "a minute=a mo|a sec",
        "quickly=sharpish|pronto", "hurry=get a move on|chop chop", "soon=in a bit|shortly",
        "cold=nippy|chilly|freezing", "it's raining=it's chucking it down|it's tipping it down|it's pouring",
        "raining=chucking it down|pouring", "go away=clear off|shove off", "shut up=pipe down|put a sock in it",
        "be quiet=pipe down|button it", "stop it=give over|knock it off|cut it out",
        "calm down=keep your hair on|calm down, mate", "run away=leg it|scarper", "flee=scarper", "escape=scarper|bolt",
        "fight=scrap|have a go", "fighting=scrapping", "kill=bump off|do in", "killed=bumped off|done in",
        "die=pop your clogs|snuff it", "died=popped their clogs|snuffed it|kicked the bucket", "dead=brown bread|done for",
        "cheap=cheap as chips", "expensive=dear|pricey|steep"
    };

    private static final String[] COCKNEY = {
        // accent: th-fronting, h-dropping, glottal stops
        "think=fink", "thing=fing", "things=fings", "nothing=nuffink", "something=somefink", "anything=anyfink",
        "everything=everyfink", "three=free", "through=frough", "with=wiv", "without=wivout", "brother=bruvver",
        "mother=muvver", "father=favver", "together=togevver", "another=anuvver", "other=uvver", "both=bofe",
        "birthday=birfday", "have='ave", "has='as", "had='ad", "him='im", "her='er", "his='is", "here='ere",
        "hello='ello|'ello guv'nor|wotcha", "hat='at", "hand='and", "how='ow", "hot='ot", "help='elp",
        "hungry='ungry", "hear='ear", "happy='appy", "heart='eart", "half='alf", "what=wot", "isn't it=innit",
        "isn't=ain't", "aren't=ain't", "am not=ain't", "water=wa'er", "better=be'er", "little=li'le", "bottle=bo'le",
        "butter=bu'er", "city=ci'y", "letter=le'er", "matter=ma'er", "later=la'er",
        "my=me", "your=yer", "you=ya", "you're=yer", "you know=y'know", "yes=yeah|yeah, course", "very=well|proper",
        "really=proper|well", "a lot=loads|a load",
        // rhyming slang
        "stairs=apples and pears", "phone=dog and bone", "wife=trouble and strife", "face=boat race", "look=butcher's",
        "mate=china", "friend=china|me old china", "money=bread|bread and honey", "tea=Rosie Lee", "suit=whistle",
        "mouth=north and south", "drunk=Brahms and Liszt", "lies=porkies", "lie=porky", "car=motor", "work=graft",
        "nose=hooter", "ears=lugholes", "eyes=mince pies", "feet=plates of meat", "road=frog and toad", "pub=boozer",
        "head=loaf", "hair=barnet", "teeth=Hampsteads", "hands=German bands", "house=drum", "party=knees-up",
        "kids=sprogs", "police=the old bill", "mad=bonkers"
    };

    private static final String[] POSH = {
        "hello=good day|how do you do|good afternoon", "hi=how do you do|good day", "hey=I say|ahoy there",
        "bye=toodle-pip|pip pip|farewell", "goodbye=farewell|toodle-pip|good day to you", "see you later=until we meet again",
        "thanks=much obliged|most kind", "thank you=thank you ever so|much obliged|most kind of you",
        "sorry=terribly sorry|frightfully sorry", "excuse me=pardon me|forgive the intrusion", "please=pray|if you would",
        "yes=indeed|quite|rather|quite so", "yeah=indeed|quite so|rather", "yep=indeed|rather", "nope=I think not",
        "okay=very well|quite all right|splendid", "ok=very well|quite all right", "wow=good heavens|gosh|my word",
        "omg=good heavens|great Scott", "oh my god=good heavens|great Scott|my word", "damn=blast|drat|botheration",
        "awesome=splendid|spiffing|marvellous|top-hole", "cool=spiffing|ripping|capital", "great=splendid|capital|jolly good",
        "amazing=marvellous|spiffing|wondrous", "nice=lovely|charming|delightful", "good=jolly good|splendid|capital",
        "fantastic=ripping|spiffing", "excellent=capital|first-rate|top-hole", "perfect=impeccable|just the ticket",
        "very=frightfully|terribly|awfully|jolly", "really=most|truly|awfully", "extremely=exceedingly|frightfully",
        "friend=old chap|old bean|dear fellow", "friends=chaps|dear fellows", "mate=old bean|old chap", "buddy=old bean",
        "dude=old chap|dear fellow", "bro=old boy", "guys=chaps|gentlemen", "guy=chap|fellow", "man=gentleman|chap",
        "men=gentlemen|chaps", "boy=young master|lad", "girl=young lady", "kids=youngsters|little ones",
        "kid=youngster", "child=little one", "children=little ones|youngsters", "mom=mother", "mum=mother", "dad=father",
        "stupid=rather dim|simple-minded", "idiot=buffoon|nincompoop|bounder", "idiots=buffoons|nincompoops",
        "dumb=rather dim", "noob=novice|greenhorn", "bad=dreadful|ghastly|beastly", "terrible=ghastly|dreadful",
        "awful=ghastly|beastly", "horrible=ghastly|frightful", "disgusting=vile|revolting", "boring=tedious|wearisome",
        "weird=peculiar|queer", "strange=peculiar|singular", "crazy=positively mad|quite deranged",
        "tired=rather fatigued|quite done in", "exhausted=utterly spent|quite done in", "hungry=rather peckish",
        "drunk=squiffy|three sheets to the wind|tipsy", "angry=positively cross|most vexed", "annoyed=rather vexed",
        "money=funds|pounds", "cash=funds", "rich=terribly wealthy|well-to-do", "poor=impoverished|of modest means",
        "toilet=lavatory|powder room", "bathroom=lavatory|washroom", "food=victuals|fare", "dinner=supper",
        "beer=ale", "party=soiree|do", "house=manor|residence", "home=abode|residence", "base=estate|manor",
        "maybe=perhaps", "probably=most likely|in all likelihood", "definitely=most assuredly|without a doubt",
        "i want=I should like|I do rather fancy", "i need=I require", "can i=might I", "can you=might you|would you be so kind as to",
        "i think=I dare say|I daresay", "i guess=I suppose", "i don't know=I haven't the foggiest|I haven't the faintest",
        "idk=I haven't the foggiest", "wait=one moment|pray wait", "hold on=one moment|one moment, if you please",
        "hurry=make haste", "quickly=posthaste|with all haste", "fast=with haste", "kill=dispatch|do away with",
        "killed=dispatched", "die=perish", "died=perished|passed on", "dead=deceased|no more", "fight=duel|spar",
        "attack=engage|set upon", "steal=pilfer|purloin", "stole=pilfered|purloined", "thief=scoundrel|rogue",
        "police=constabulary|the bobbies", "cops=constabulary", "going to=going to", "gonna=going to", "wanna=wish to",
        "lol=ha ha, how droll", "lmao=oh, how amusing", "lots of=a great many|a great deal of", "a lot of=a great deal of",
        "a lot=a great deal", "shut up=do be quiet|pray, hold your tongue", "be quiet=pray, silence",
        "go away=be off with you|begone", "calm down=pray, compose yourself", "stop it=I say, desist",
        "cold=rather brisk|decidedly chilly", "raining=pouring most dreadfully", "hell=blazes"
    };

    private static final String[] SCOTTISH = {
        "hello=hullo|hiya|awright|how's it gaun", "hi=awright|hiya", "hey=awright", "yes=aye", "yeah=aye", "yep=aye",
        "no=naw|nae", "nope=naw", "nah=naw", "small=wee", "little=wee", "tiny=wee tiny", "a bit=a wee bit|a wee bittie",
        "a little=a wee", "child=wean|bairn", "baby=wean|wee bairn", "kid=bairn|wean", "kids=bairns|weans",
        "children=bairns|weans", "boy=laddie", "boys=laddies", "girl=lassie", "girls=lassies", "woman=wifie",
        "man=fella", "friend=pal", "mate=pal", "know=ken", "knew=kent", "i know=I ken", "do you know=dae ye ken",
        "you know=ye ken", "don't=dinnae", "do not=dinnae", "can't=cannae", "cannot=cannae", "can not=cannae",
        "couldn't=couldnae", "wouldn't=wouldnae", "shouldn't=shouldnae", "didn't=didnae", "doesn't=doesnae",
        "isn't=isnae", "wasn't=wasnae", "won't=willnae", "haven't=havenae", "hasn't=hasnae", "aren't=arenae",
        "weren't=werenae", "gonna=gonnae", "you=ye", "your=yer", "you're=ye're", "yourself=yersel",
        "myself=masel", "my=ma", "of=o'", "to=tae", "do=dae", "from=fae", "off=aff", "out=oot", "about=aboot",
        "down=doon", "house=hoose", "home=hame", "town=toon", "now=noo", "how=hoo", "own=ain", "old=auld",
        "cold=freezing|baltic", "very=awfy|pure|dead", "really=pure|awfy", "pretty=bonnie", "beautiful=bonnie|braw",
        "good=braw|grand", "great=braw|barry", "awesome=braw|pure barry", "nice=braw|lovely", "stupid=daft|glaikit",
        "idiot=eejit|numpty|bampot|dafty", "crazy=bonkers|aff yer heid", "head=heid", "night=nicht", "tonight=the nicht",
        "tomorrow=the morra", "today=the day", "rain=dreich", "raining=chucking it doon|pouring doon",
        "drunk=steamin|mortal|blootered", "tired=knackered|puggled", "hungry=starvin", "sad=doon in the dumps|sair",
        "thank you=cheers|thanks a million|ta", "thanks=cheers|ta", "goodbye=cheerio|haste ye back|see ye",
        "bye=cheerio|see ye|tatty bye", "see you=see ye", "what=whit", "why=how", "where=whaur", "wait=haud on",
        "hold on=haud on|haud yer horses", "stop=stoap", "shut up=wheesht|haud yer wheesht", "be quiet=wheesht",
        "look=keek", "look at=keek at", "cry=greet", "crying=greetin", "talk=blether", "talking=blethering",
        "chat=blether", "rubbish=pish|havers", "nonsense=havers|haivers", "wow=jings|crivens|michty me",
        "oh my god=jings|crivens|michty me", "omg=jings|michty me", "a lot=a wheen|loads",
        "okay=nae bother|aye okay", "ok=nae bother|aye ok", "no problem=nae bother|nae worries",
        "i don't know=I dinnae ken|nae idea", "i dont know=I dinnae ken", "idk=dinnae ken", "all=a'", "ask=ax"
    };

    private static final String[] NORTHERN = {
        "the=t'", "hello=ey up|hiya|now then", "hi=ey up|now then", "hey=ey up", "yes=aye|ay", "yeah=aye|ay",
        "nothing=nowt", "something=summat", "anything=owt", "very=reet|right|proper", "really=reet|right|proper",
        "good=champion|grand|reet good", "great=champion|grand|belting", "awesome=champion|belting|cracking",
        "nice=grand|lovely|champion", "excellent=champion|cracking", "amazing=belting|champion", "cool=champion|grand",
        "friend=mate|love|pal", "mate=love|pal", "kid=bairn|nipper", "kids=bairns|nippers", "child=bairn",
        "woman=lass", "girl=lass", "boy=lad", "man=lad|fella", "mom=mam", "mum=mam", "grandma=nan|nana",
        "grandpa=grandad", "my=me", "isn't=int", "cold=nithering|brass monkeys", "raining=chucking it down|siling it down",
        "tired=knackered|jiggered|fair worn out", "hungry=clemmed|famished", "drunk=ratted|steaming|legless",
        "stupid=daft|soft|daft as a brush", "idiot=daft lad|numpty|wazzock", "crazy=barmy|bonkers|daft",
        "angry=fuming|vexed|mithered", "annoyed=mithered|vexed|cheesed off", "sad=gutted", "party=do|shindig",
        "pub=boozer|local", "food=grub|snap", "lunch=dinner", "dinner=tea", "sandwich=butty|sarnie|barm",
        "money=brass|dosh|wonga", "rich=minted|rolling in brass", "poor=skint|brassic", "toilet=lav|netty|loo",
        "thanks=ta|cheers|ta very much", "thank you=ta very much|cheers love", "goodbye=ta-ra|tara|see thee",
        "bye=ta-ra|tarra|cheers", "hurry=get a shift on|look sharp", "wait=hang on a bit|howd on",
        "hold on=howd on|hang about", "stop it=give over", "shut up=shut thi gob|give over|belt up",
        "be quiet=shut thi gob|belt up", "calm down=keep thi hair on|calm thissen", "talking=chuntering|gabbing",
        "talk=natter|chunter", "chat=natter|chinwag", "crying=blarting", "cry=blart", "sleep=kip", "sleeping=kipping",
        "maybe=happen|mebbe", "perhaps=happen", "probably=happen", "a lot=loads|a fair bit", "you=tha",
        "your=thy", "yourself=thissen", "myself=missen"
    };

    private static final String[] WELSH = {
        "hello=alright|shwmae|alright butt", "hi=alright|shwmae", "hey=alright butt", "yes=aye|yes mun", "yeah=aye|yes mun",
        "no=no mun", "friend=butt|mun|boyo", "mate=butt|mun", "buddy=butt", "dude=mun", "guys=boys|lads|butties",
        "man=boyo|mun", "boy=boyo", "girl=cariad", "love=cariad", "darling=cariad", "little=bach|tiny bit",
        "small=bach|little", "good=tidy|lush|good", "great=tidy|lush", "nice=lush|tidy|lovely", "awesome=lush|tidy|mint",
        "amazing=lush|mint", "cool=tidy|lush|mint", "very=proper|dead|well", "really=proper|well|dead",
        "stupid=twp|daft", "idiot=twp lad|numpty", "crazy=mental|barmy", "tired=knackered|worn out",
        "hungry=starving|peckish", "thanks=diolch|cheers|ta", "thank you=diolch|thanks a lot|cheers",
        "thanks a lot=diolch yn fawr", "goodbye=hwyl|ta-ra|see you now in a minute", "bye=hwyl|ta-ra",
        "see you=see you now|see you in a minute", "soon=now in a minute|in a minute", "later=now in a minute",
        "in a minute=now in a minute", "wait=hang on a minute|wait now", "hold on=hang on a minute|hold on now",
        "cuddle=cwtch", "hug=cwtch", "i don't know=I don't know now", "kids=little ones", "children=little ones",
        "mum=mam", "mom=mam", "grandma=nan|mamgu", "grandpa=taid|grandad", "beer=pint|bevvy", "pub=boozer|local",
        "party=do|shindig", "tea=a cuppa|tea", "money=dosh|wonga", "toilet=lav|loo", "omg=duw duw|blimey|there's lovely",
        "oh my god=duw duw|bloody hell|there's lovely", "wow=duw|blimey|there's lovely", "damn=duw|blast",
        "okay=tidy|alright|lush", "ok=tidy|alright|iawn", "fine=iawn|tidy|grand"
    };

    /** Pompous, old-fashioned "thou art a most splendid fellow" speech, in the style of Rouxls Kaard. */
    private static final String[] ROUXLS = {
        "you=thou|thee", "you're=thou art", "you are=thou art", "are you=art thou", "do you=dost thou",
        "can you=canst thou", "will you=wilt thou", "would you=wouldst thou", "your=thy", "yours=thine",
        "yourself=thyself", "my=mine own", "has=hath", "does=doth", "why=wherefore", "here=hither", "there=thither",
        "now=forthwith", "soon=anon|ere long", "maybe=perchance|mayhap", "perhaps=mayhap", "before=ere",
        "over there=yonder", "i'm=I am", "i think=methinks|I deem", "i guess=I suppose", "i want=I desire",
        "i need=I require", "can i=may I", "hello=hail|well met|greetings, good traveler", "hi=hail|well met",
        "hey=hark|ho there", "bye=fare thee well|adieu", "goodbye=farewell, good sir|adieu",
        "see you=until we meet again", "thanks=I thank thee|much gratitude", "thank you=I thank thee|my gratitude",
        "please=prithee|I beseech thee", "sorry=I beg thy pardon|forgive me", "excuse me=pardon me, good sir",
        "yes=aye|verily|indeed", "yeah=aye|verily", "yep=aye", "no=nay", "nope=nay", "ok=very well|so be it",
        "okay=so be it|very well", "friend=good fellow|comrade", "friends=good fellows", "buddy=good fellow",
        "dude=good sir", "bro=good sir", "guys=good sirs", "guy=fellow", "man=good sir", "boy=lad", "girl=fair maiden",
        "kids=younglings", "mom=mother", "mum=mother", "dad=father", "everyone=all and sundry",
        "awesome=splendid|most magnificent|exquisite", "cool=splendid|exquisite", "great=splendid|magnificent",
        "amazing=astounding|marvelous", "nice=exquisite|most agreeable", "good=splendid|fine",
        "fantastic=magnificent", "perfect=flawless", "bad=most foul|dreadful", "terrible=dreadful|abominable",
        "awful=abominable", "boring=tedious", "weird=peculiar", "very=most|exceedingly|truly", "really=truly|verily",
        "idiot=fool|knave|dunce", "stupid=foolish|dull-witted", "noob=novice", "tired=weary", "hungry=famished",
        "angry=wrathful", "sad=sorrowful", "wow=gadzooks|egads|zounds", "omg=egads|zounds", "oh my god=egads|zounds",
        "damn=curses|fie", "lol=hee hee hee", "lmao=hee hee hee", "haha=hee hee hee", "gg=well played, good sir",
        "kill=slay|vanquish", "die=perish", "dead=slain", "fight=do battle|duel", "steal=pilfer", "money=coin|gold",
        "toilet=privy", "house=castle", "home=castle", "base=castle|keep", "food=victuals", "beer=ale|mead",
        "party=feast|revelry", "stop=cease|desist", "wait=tarry|hold", "hurry=make haste", "quickly=posthaste",
        "go away=begone", "shut up=hold thy tongue", "be quiet=hush", "i don't know=I know not|I haven't the foggiest",
        "idk=I know not", "a lot=a great many", "lots of=a great many", "what's up=what news", "how are you=how dost thou fare",
        // gaming slang
        "gg ez=a most splendid, effortless victory", "gg=a splendid match, good sir", "ggs=a splendid match, good sirs",
        "ez=effortless|a mere trifle", "ez win=an effortless victory", "easy=effortless|a mere trifle",
        "gl=good fortune to thee", "glhf=good fortune and merry sport", "gl hf=good fortune and merry sport", "hf=merry sport", "wp=well played, good sir",
        "gj=well done, good sir", "nt=nobly tried", "ns=nobly struck", "rip=alas, he hath perished", "f=alas",
        "rekt=utterly vanquished", "owned=thoroughly bested", "pwned=thoroughly bested", "get rekt=be vanquished",
        "op=unfairly mighty", "noobs=novices", "scrub=wretched novice", "toxic=most venomous", "trash=refuse",
        "carry=bear upon thy shoulders", "carried=borne upon the shoulders of another", "hacker=sorcerer", "hackers=sorcerers",
        "hacks=sorcery", "hacking=sorcery", "cheater=scoundrel and cheat", "cheating=cheating most foul",
        "cheaters=scoundrels and cheats", "pvp=a duel", "afk=away from my keep", "brb=I shall return anon",
        "be right back=I shall return anon", "bbl=I shall return anon", "gtg=I must depart", "g2g=I must depart",
        "ttyl=we shall speak anon", "gn=good night, fair traveler", "nvm=pay it no heed", "never mind=pay it no heed",
        "idc=I care not", "np=no trouble at all", "no problem=no trouble at all", "ty=I thank thee", "thx=I thank thee",
        "yw=thou art most welcome", "pls=prithee", "plz=prithee", "ily=I hold thee dear", "wtf=what in blazes",
        "omfg=egads and zounds", "smh=I shake mine head", "imo=in mine humble opinion", "tbh=in truth", "fr=in truth",
        "ngl=I shall not lie", "irl=in the waking world", "lmk=prithee inform me", "pog=glorious", "poggers=glorious",
        "sus=most suspicious", "cap=a falsehood", "no cap=in truth", "lag=delay", "laggy=sluggish", "xd=hee hee hee",
        "lmfao=hee hee hee", "rofl=hee hee hee", "ur=thy", "u=thou", "r=art", "ya=thou", "gonna=shall", "wanna=wish to",
        "gotta=must", "kinda=somewhat", "bruh=good heavens", "yo=ho there", "sup=what news"
    };

    private static final String[] TAILS_ROUXLS = {"hee hee hee", "hoo hoo hoo", "or so I deem", "good sir", "verily"};

    private static final String[] TAILS_BRITISH = {"mate", "innit", "yeah", "lad", "cheers"};
    private static final String[] TAILS_COCKNEY = {"guv'nor", "innit", "me old china", "mate", "bruv", "know wot I mean"};
    private static final String[] TAILS_POSH = {"old bean", "what", "I dare say", "don't you know", "old chap"};
    private static final String[] TAILS_SCOTTISH = {"aye", "ken", "pal", "so it is", "wee man"};
    private static final String[] TAILS_NORTHERN = {"love", "lad", "by 'eck", "duck", "pet"};
    private static final String[] TAILS_WELSH = {"mun", "butt", "boyo", "see", "there's lovely"};

    private static final Set<String> KEEP_ING = Set.of(
        "during", "morning", "evening", "ceiling", "nothing", "something", "anything", "everything");

    private static final Pattern ING = Pattern.compile("\\b(\\p{L}*[aeiouyAEIOUY]\\p{L}*)ing\\b");

    // ------------------------------------------------------------------ engine

    private static Map<String, String[]> merge(String[]... sources) {
        Map<String, String[]> map = new LinkedHashMap<>();
        for (String[] src : sources) {
            for (String e : src) {
                int eq = e.indexOf('=');
                if (eq <= 0) continue;
                map.put(e.substring(0, eq).trim().toLowerCase(Locale.ROOT), e.substring(eq + 1).split("\\|"));
            }
        }
        return map;
    }

    private static synchronized void init(Mode m) {
        if (m.pattern != null) return;
        switch (m) {
            case BRITISH -> { m.words = merge(SPELLING, BRITISH); m.tails = TAILS_BRITISH; }
            case COCKNEY -> { m.words = merge(SPELLING, BRITISH, COCKNEY); m.tails = TAILS_COCKNEY; m.dropG = true; }
            case POSH -> { m.words = merge(SPELLING, POSH); m.tails = TAILS_POSH; }
            case SCOTTISH -> { m.words = merge(SPELLING, BRITISH, SCOTTISH); m.tails = TAILS_SCOTTISH; }
            case NORTHERN -> { m.words = merge(SPELLING, BRITISH, NORTHERN); m.tails = TAILS_NORTHERN; }
            case WELSH -> { m.words = merge(SPELLING, BRITISH, WELSH); m.tails = TAILS_WELSH; }
            case ROUXLS -> { m.words = merge(SPELLING, ROUXLS, RouxlsGamer.WORDS, RouxlsGamer.ABBREVIATIONS); m.tails = TAILS_ROUXLS; }
        }
        List<String> keys = new ArrayList<>(m.words.keySet());
        keys.sort(Comparator.comparingInt(String::length).reversed());
        StringBuilder alt = new StringBuilder();
        for (String k : keys) {
            if (alt.length() > 0) alt.append('|');
            String[] parts = k.split(" ");
            for (int i = 0; i < parts.length; i++) {
                if (i > 0) alt.append("\\s+");
                alt.append(Pattern.quote(parts[i]));
            }
        }
        m.pattern = Pattern.compile(
            "(?<![\\p{L}\\p{N}_@/.:'-])(?:" + alt + ")(?![\\p{L}\\p{N}_@/:'-])",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    /** Rewrites English text in the given dialect. */
    public static String apply(String text, Mode mode) {
        init(mode);
        String in = text.replace('’', '\'');
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        Matcher mt = mode.pattern.matcher(in);
        StringBuilder out = new StringBuilder();
        while (mt.find()) {
            String key = mt.group().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
            String[] opts = mode.words.get(key);
            String rep = opts == null ? mt.group() : matchCase(mt.group(), opts[rnd.nextInt(opts.length)]);
            mt.appendReplacement(out, Matcher.quoteReplacement(rep));
        }
        mt.appendTail(out);
        String result = out.toString();
        if (mode.dropG) result = dropG(result);
        return addTail(result, mode.tails, rnd);
    }

    private static String matchCase(String src, String rep) {
        if (rep.isEmpty()) return rep;
        // Only shout back for real shouted words (4+ letters); "GG" or "OMG" just start with a capital.
        boolean allUpper = src.length() > 3 && src.equals(src.toUpperCase(Locale.ROOT))
            && !src.equals(src.toLowerCase(Locale.ROOT));
        if (allUpper) return rep.toUpperCase(Locale.ROOT);
        if (Character.isUpperCase(src.charAt(0))) return Character.toUpperCase(rep.charAt(0)) + rep.substring(1);
        return rep;
    }

    private static String dropG(String s) {
        Matcher m = ING.matcher(s);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String whole = m.group();
            String rep = KEEP_ING.contains(whole.toLowerCase(Locale.ROOT)) ? whole : m.group(1) + "in'";
            m.appendReplacement(sb, Matcher.quoteReplacement(rep));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String addTail(String s, String[] tails, ThreadLocalRandom rnd) {
        if (tails.length == 0 || s.length() < 8 || rnd.nextDouble() > 0.35) return s;
        int end = s.length();
        while (end > 0 && ".!?".indexOf(s.charAt(end - 1)) >= 0) end--;
        String body = s.substring(0, end);
        String punct = s.substring(end);
        String tail = tails[rnd.nextInt(tails.length)];
        if (body.isEmpty() || body.toLowerCase(Locale.ROOT).endsWith(tail.toLowerCase(Locale.ROOT))) return s;
        return body + ", " + tail + punct;
    }
}
