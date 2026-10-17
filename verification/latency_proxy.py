"""FIFO loopback delay, one-way delay applied independently without packet reordering."""
import asyncio,time,threading
class DelayProxy:
 def __init__(self,target_port,delay=.1):self.target=target_port;self.delay=delay;self.ready=threading.Event();self.port=None;self.loop=None
 def start(self):threading.Thread(target=self.run,daemon=True).start();self.ready.wait(5);return self.port
 def run(self):
  self.loop=asyncio.new_event_loop();asyncio.set_event_loop(self.loop);self.loop.run_until_complete(self.serve());self.loop.run_forever()
 async def serve(self):
  self.server=await asyncio.start_server(self.handle,'127.0.0.1',0);self.port=self.server.sockets[0].getsockname()[1];self.ready.set()
 async def pipe(self,reader,writer):
  queue=asyncio.Queue()
  async def receive():
   while data:=await reader.read(65536):await queue.put((time.monotonic()+self.delay,data))
   await queue.put((0,None))
  async def send():
   while True:
    when,data=await queue.get()
    if data is None:break
    await asyncio.sleep(max(0,when-time.monotonic()));writer.write(data);await writer.drain()
   writer.close()
  await asyncio.gather(receive(),send())
 async def handle(self,reader,writer):
  upstream,output=await asyncio.open_connection('127.0.0.1',self.target)
  try:await asyncio.gather(self.pipe(reader,output),self.pipe(upstream,writer))
  except (ConnectionError,asyncio.CancelledError):pass
 def stop(self):
  if self.loop:self.loop.call_soon_threadsafe(self.loop.stop)
