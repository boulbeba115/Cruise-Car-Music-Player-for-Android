# Placeholder covers

The covers shown for music without art (CHANGE_REQUESTS.md CR-13), in `core/ui/src/main/res/drawable-nodpi/` as
`cover_<skin>_<nn>.webp`. Each skin has its own set of illustrated scenes in its style; `PlaceholderCovers` picks one
per song (from the song's path), so songs in one folder get different covers and a song keeps its cover. A skin
without a set of its own would borrow from all the others.

Made on 2026-10-24 with the owner's local ComfyUI and its Z-Image Turbo workflow (Z-Image Turbo bf16, Qwen3-4B text
encoder, `ae` VAE; 1024 × 1024, 8 steps, CFG 1, res_multistep / simple, AuraFlow shift 3), then resized to
512 × 512 WebP (quality 78): 158 covers, about 2.5 MB in all. 160 were generated, 20 per skin; amber 03 (a real car maker's badge on the steering wheel), amber 08 (came out with a white frame) were left out.
Seed = 20000 + 7919 × (generated number − 1) + (sum of the skin id's character codes mod 997).

Prompt = *subject*, *skin style*, *full-bleed image that fills the whole frame edge to edge, no border, no frame, no mockup, no text, no letters, no words, no logo, no watermark*

## midnight

Style: *cozy digital illustration, deep blue and violet night palette, soft glow, gentle grain*

| File | Generated | Subject |
|---|---|---|
| cover_midnight_01.webp | 01 | a fox wearing headphones sitting on a rooftop under the full moon |
| cover_midnight_02.webp | 02 | a girl reading by an open window at night next to a record player, city lights outside |
| cover_midnight_03.webp | 03 | an owl DJ on a tree branch with tiny turntables under the stars |
| cover_midnight_04.webp | 04 | a night train with lit windows crossing a bridge in the mountains |
| cover_midnight_05.webp | 05 | an astronaut floating above the earth holding a cassette player |
| cover_midnight_06.webp | 06 | a cat sleeping on a grand piano in moonlight |
| cover_midnight_07.webp | 07 | a quiet street at night with a vintage car parked by a glowing diner |
| cover_midnight_08.webp | 08 | glowing jellyfish drifting in a deep blue sea like lanterns |
| cover_midnight_09.webp | 09 | a campfire under the milky way with a guitar leaning on a log |
| cover_midnight_10.webp | 10 | a rabbit with big headphones watching shooting stars from a hill |
| cover_midnight_11.webp | 11 | a small ferry crossing a calm bay at night with city lights behind |
| cover_midnight_12.webp | 12 | a couple dancing slowly on a balcony at night |
| cover_midnight_13.webp | 13 | a bear playing saxophone on a snowy night street under a lamp |
| cover_midnight_14.webp | 14 | a bedroom with fairy lights, vinyl records on the floor and a sleeping dog |
| cover_midnight_15.webp | 15 | a whale swimming through the night sky among stars over a town |
| cover_midnight_16.webp | 16 | a rowing boat on a lake reflecting the full moon |
| cover_midnight_17.webp | 17 | a rainy window at night with a cup of tea and headphones on the sill |
| cover_midnight_18.webp | 18 | a mountain cabin at night with warm light in the windows |
| cover_midnight_19.webp | 19 | a violinist playing on a moonlit stone bridge |
| cover_midnight_20.webp | 20 | a lighthouse at night sending a soft beam over the sea |

## neon

Style: *neon cyberpunk illustration, magenta and cyan neon glow, rain reflections on wet ground, cinematic night city*

| File | Generated | Subject |
|---|---|---|
| cover_neon_01.webp | 01 | a cat wearing neon headphones sitting on a rainy cyberpunk street |
| cover_neon_02.webp | 02 | a girl with a transparent umbrella walking under glowing signs in the rain |
| cover_neon_03.webp | 03 | a retro sports car driving through a neon-lit tunnel |
| cover_neon_04.webp | 04 | a steaming noodle stall in a narrow neon alley at night |
| cover_neon_05.webp | 05 | a DJ booth on a rooftop with a glowing city skyline behind |
| cover_neon_06.webp | 06 | a friendly robot playing an electric guitar in a neon arcade |
| cover_neon_07.webp | 07 | glowing koi fish swimming through the air above a rainy street |
| cover_neon_08.webp | 08 | a skateboarder rolling under a neon overpass |
| cover_neon_09.webp | 09 | glowing vending machines lining a quiet alley at night |
| cover_neon_10.webp | 10 | an empty subway car at night with neon light streaking past the windows |
| cover_neon_11.webp | 11 | a fox in a hoodie waiting at a neon crosswalk |
| cover_neon_12.webp | 12 | a motorbike rider stopped at a glowing traffic light in the rain |
| cover_neon_13.webp | 13 | a rooftop pool lit by neon lights above the city |
| cover_neon_14.webp | 14 | a small cassette shop at night with a glowing shop window |
| cover_neon_15.webp | 15 | a rain-soaked phone booth glowing pink on an empty street |
| cover_neon_16.webp | 16 | a neon tiger walking on wet asphalt |
| cover_neon_17.webp | 17 | a view of glowing towers through an apartment window at night |
| cover_neon_18.webp | 18 | the entrance of a jazz bar glowing with neon tubes |
| cover_neon_19.webp | 19 | a drone view of a neon intersection at night with light trails |
| cover_neon_20.webp | 20 | a girl with headphones on a fire escape above a neon street |

## amber

Style: *dynamic automotive illustration, graphite tones with signal red and ice blue accents, motion, cinematic lighting*

| File | Generated | Subject |
|---|---|---|
| cover_amber_01.webp | 01 | a sports car on a winding mountain pass at dawn |
| cover_amber_02.webp | 02 | a race car in the pit lane at night under floodlights |
| — (left out) | 03 | the view over a steering wheel onto a winding coastal road |
| cover_amber_03.webp | 04 | a vintage rally car splashing through a muddy forest track |
| cover_amber_04.webp | 05 | a motorcycle leaning into a curve on a race track |
| cover_amber_05.webp | 06 | a drifting car with tire smoke under stadium lights |
| cover_amber_06.webp | 07 | a classic convertible on a desert highway at sunset |
| — (left out) | 08 | a garage workshop with a classic car under warm lamps |
| cover_amber_07.webp | 09 | a race car speeding past a blurred crowd |
| cover_amber_08.webp | 10 | an off-road car crossing a red rock canyon |
| cover_amber_09.webp | 11 | a sports car on a rainy city street at night |
| cover_amber_10.webp | 12 | a karting track seen from above |
| cover_amber_11.webp | 13 | a tunnel with light streaks from passing cars |
| cover_amber_12.webp | 14 | an old gas station at dusk with a muscle car |
| cover_amber_13.webp | 15 | a rally car on a snowy forest road |
| cover_amber_14.webp | 16 | a bridge at night with long car light trails |
| cover_amber_15.webp | 17 | an electric car charging under a starry sky |
| cover_amber_16.webp | 18 | aerial view of mountain road switchbacks at sunrise |
| cover_amber_17.webp | 19 | a car headlight glowing in fog on an empty road |
| cover_amber_18.webp | 20 | a racing helmet resting on a car seat |

## vinyl

Style: *cozy flat storybook illustration, warm muted beige terracotta and olive palette, soft paper grain texture, gentle warm lighting*

| File | Generated | Subject |
|---|---|---|
| cover_vinyl_01.webp | 01 | a white cat with headphones lounging by a window next to a record player, plants and a lamp |
| cover_vinyl_02.webp | 02 | a dog sleeping next to a turntable on a soft rug |
| cover_vinyl_03.webp | 03 | a cozy living room with stacks of records, plants and a warm lamp |
| cover_vinyl_04.webp | 04 | a grandmother knitting while a record player spins |
| cover_vinyl_05.webp | 05 | a jazz trio playing in a small warm cafe |
| cover_vinyl_06.webp | 06 | a bookshelf full of vinyl records beside a reading armchair |
| cover_vinyl_07.webp | 07 | a rainy afternoon with tea and a spinning record by the window |
| cover_vinyl_08.webp | 08 | a hamster dancing on a spinning record |
| cover_vinyl_09.webp | 09 | a record store with someone browsing crates of records |
| cover_vinyl_10.webp | 10 | an old radio on a wooden table with a vase of flowers |
| cover_vinyl_11.webp | 11 | a couple slow dancing in a small kitchen |
| cover_vinyl_12.webp | 12 | a pianist in a cozy piano bar at night |
| cover_vinyl_13.webp | 13 | a teddy bear listening to a gramophone |
| cover_vinyl_14.webp | 14 | a garden picnic with a portable record player |
| cover_vinyl_15.webp | 15 | a sunlit attic with a guitar and boxes of records |
| cover_vinyl_16.webp | 16 | a fox playing a cello in a cozy room |
| cover_vinyl_17.webp | 17 | a small bakery in the morning with a radio on the counter |
| cover_vinyl_18.webp | 18 | a camper van parked by a lake at sunset with music playing |
| cover_vinyl_19.webp | 19 | an owl reading sheet music by candlelight |
| cover_vinyl_20.webp | 20 | a cat curled up asleep on a stack of records |

## tape

Style: *retro 1980s illustration, teal coral and cream palette, subtle halftone print texture, nostalgic summer mood*

| File | Generated | Subject |
|---|---|---|
| cover_tape_01.webp | 01 | a boombox on the beach at sunset |
| cover_tape_02.webp | 02 | a roller skater in a retro roller rink |
| cover_tape_03.webp | 03 | kids riding bikes through a suburb at dusk |
| cover_tape_04.webp | 04 | a cassette mixtape on a bed in a teenage bedroom with posters |
| cover_tape_05.webp | 05 | glowing arcade cabinets in a retro arcade |
| cover_tape_06.webp | 06 | a walkman and sunglasses on a pool towel |
| cover_tape_07.webp | 07 | a car with pop-up headlights on a palm tree road |
| cover_tape_08.webp | 08 | an aerobics class in bright leotards |
| cover_tape_09.webp | 09 | a drive-in cinema at night with a blank glowing screen |
| cover_tape_10.webp | 10 | a retro bedroom with a small TV and video tapes |
| cover_tape_11.webp | 11 | a breakdancer spinning on a cardboard mat in the city |
| cover_tape_12.webp | 12 | a vintage diner with a glowing jukebox |
| cover_tape_13.webp | 13 | a camper van with surfboards on the roof by the sea |
| cover_tape_14.webp | 14 | a synthesizer in a retro recording studio |
| cover_tape_15.webp | 15 | a skate park at golden hour |
| cover_tape_16.webp | 16 | friends hanging out on a rooftop in summer |
| cover_tape_17.webp | 17 | a retro shopping mall with fountains and palm trees |
| cover_tape_18.webp | 18 | a bunny wearing a walkman in a flower meadow |
| cover_tape_19.webp | 19 | a boombox on a fire escape on a summer evening |
| cover_tape_20.webp | 20 | a lighthouse on a cliff under a big retro sunset |

## aurora

Style: *dreamy painterly illustration, mint green and violet sky, soft light, magical calm atmosphere*

| File | Generated | Subject |
|---|---|---|
| cover_aurora_01.webp | 01 | northern lights over a frozen lake with a lone cabin |
| cover_aurora_02.webp | 02 | a deer standing in snow under the aurora |
| cover_aurora_03.webp | 03 | a girl lying in a meadow looking up at the stars |
| cover_aurora_04.webp | 04 | a glowing forest full of fireflies |
| cover_aurora_05.webp | 05 | a whale breaching under the aurora |
| cover_aurora_06.webp | 06 | snowy mountains under green northern lights |
| cover_aurora_07.webp | 07 | a tent glowing under the milky way and the aurora |
| cover_aurora_08.webp | 08 | a fox on a snowy hill under dancing lights |
| cover_aurora_09.webp | 09 | a waterfall at night with glowing mist |
| cover_aurora_10.webp | 10 | a polar bear looking up at the sky |
| cover_aurora_11.webp | 11 | floating islands in a dreamy pastel sky |
| cover_aurora_12.webp | 12 | a blue glacier cave with soft light |
| cover_aurora_13.webp | 13 | a child with a lantern walking in a snowy forest |
| cover_aurora_14.webp | 14 | a lake with glowing lily pads at night |
| cover_aurora_15.webp | 15 | a hot spring steaming under the aurora |
| cover_aurora_16.webp | 16 | an owl flying across the northern lights |
| cover_aurora_17.webp | 17 | a sea of clouds at dawn around a mountain peak |
| cover_aurora_18.webp | 18 | wildflowers glowing in moonlight |
| cover_aurora_19.webp | 19 | a kayak on a mirror-still lake under a green sky |
| cover_aurora_20.webp | 20 | a small village in the snow under the aurora |

## daylight

Style: *bright cheerful illustration, sunny pastel palette, clean shapes, soft shadows*

| File | Generated | Subject |
|---|---|---|
| cover_daylight_01.webp | 01 | a sunflower field with a picnic blanket and a little radio |
| cover_daylight_02.webp | 02 | a beach umbrella and headphones on warm sand |
| cover_daylight_03.webp | 03 | colorful kites over a green hill |
| cover_daylight_04.webp | 04 | a bicycle with a basket of flowers on a sunny street |
| cover_daylight_05.webp | 05 | a lemonade stand with a small speaker |
| cover_daylight_06.webp | 06 | a sailboat on a bright blue sea |
| cover_daylight_07.webp | 07 | a child dancing in a garden sprinkler |
| cover_daylight_08.webp | 08 | a sunny balcony with plants and a guitar in the morning |
| cover_daylight_09.webp | 09 | street musicians at a busy farmers market |
| cover_daylight_10.webp | 10 | hot air balloons over green fields |
| cover_daylight_11.webp | 11 | a dog running on the beach with a frisbee |
| cover_daylight_12.webp | 12 | a swing hanging from a big tree in summer |
| cover_daylight_13.webp | 13 | colorful houses along a canal |
| cover_daylight_14.webp | 14 | a picnic blanket with strawberries and a ukulele |
| cover_daylight_15.webp | 15 | a butterfly garden in spring |
| cover_daylight_16.webp | 16 | a surfer riding a sunny wave |
| cover_daylight_17.webp | 17 | a street musician playing on a park bench |
| cover_daylight_18.webp | 18 | a lemon orchard in bright sunshine |
| cover_daylight_19.webp | 19 | a cat sunbathing on a windowsill full of plants |
| cover_daylight_20.webp | 20 | a rowing boat on a pastel lake |

## oled

Style: *minimalist illustration on a pure black background, a single subject in elegant white line art with subtle grey shading, lots of empty black space*

| File | Generated | Subject |
|---|---|---|
| cover_oled_01.webp | 01 | a single feather |
| cover_oled_02.webp | 02 | a pair of headphones |
| cover_oled_03.webp | 03 | a lone tree on a hill |
| cover_oled_04.webp | 04 | a crescent moon over gentle waves |
| cover_oled_05.webp | 05 | a sitting cat |
| cover_oled_06.webp | 06 | a single rose |
| cover_oled_07.webp | 07 | a vinyl record |
| cover_oled_08.webp | 08 | a mountain range |
| cover_oled_09.webp | 09 | a hand holding a flower |
| cover_oled_10.webp | 10 | a bird in flight |
| cover_oled_11.webp | 11 | a lighthouse |
| cover_oled_12.webp | 12 | an acoustic guitar |
| cover_oled_13.webp | 13 | a curling ocean wave |
| cover_oled_14.webp | 14 | a fox face |
| cover_oled_15.webp | 15 | a paper boat |
| cover_oled_16.webp | 16 | a steaming cup of tea |
| cover_oled_17.webp | 17 | a koi fish |
| cover_oled_18.webp | 18 | a vintage microphone |
| cover_oled_19.webp | 19 | a dandelion |
| cover_oled_20.webp | 20 | a whale tail |
