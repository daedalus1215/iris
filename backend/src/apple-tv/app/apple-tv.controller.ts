import { Controller, Post, Get, Body } from '@nestjs/common';
import { AppleTvService } from '../domain/apple-tv.service';

@Controller('apple-tv')
export class AppleTvController {
  constructor(private readonly appleTvService: AppleTvService) {}

  async execute(mac: string, protocol: string) {
    return this.appleTvService.connect(mac, protocol);
  }

  @Post('connect')
  async connect(@Body('mac') mac: string, @Body('protocol') protocol: string) {
    return this.appleTvService.connect(mac, protocol);
  }

  @Post('complete-pairing')
  async completePairing(
    @Body('pairingId') pairingId: string,
    @Body('code') code: string,
  ) {
    return this.appleTvService.completePairing(pairingId, code);
  }

  @Post('up')
  async up(@Body('mac') mac: string) {
    return this.appleTvService.up(mac);
  }

  @Post('down')
  async down(@Body('mac') mac: string) {
    return this.appleTvService.down(mac);
  }

  @Post('left')
  async left(@Body('mac') mac: string) {
    return this.appleTvService.left(mac);
  }

  @Post('right')
  async right(@Body('mac') mac: string) {
    return this.appleTvService.right(mac);
  }

  @Post('select')
  async select(@Body('mac') mac: string) {
    return this.appleTvService.select(mac);
  }

  @Post('menu')
  async menu(@Body('mac') mac: string) {
    return this.appleTvService.menu(mac);
  }

  @Post('click')
  async click(@Body('mac') mac: string) {
    return this.appleTvService.click(mac);
  }

  @Post('home-home')
  async holdHome(@Body('mac') mac: string) {
    return this.appleTvService.holdHome(mac);
  }

  @Post('home')
  async home(@Body('mac') mac: string) {
    return this.appleTvService.home(mac);
  }

  @Post('play')
  async play(@Body('mac') mac: string) {
    return this.appleTvService.play(mac);
  }

  @Post('pause')
  async pause(@Body('mac') mac: string) {
    return this.appleTvService.pause(mac);
  }

  @Post('next')
  async next(@Body('mac') mac: string) {
    return this.appleTvService.next(mac);
  }

  @Post('previous')
  async previous(@Body('mac') mac: string) {
    return this.appleTvService.previous(mac);
  }

  @Post('volume-up')
  async volumeUp(@Body('mac') mac: string) {
    return this.appleTvService.volumeUp(mac);
  }

  @Post('volume-down')
  async volumeDown(@Body('mac') mac: string) {
    return this.appleTvService.volumeDown(mac);
  }

  @Post('power-off')
  async powerOff(@Body('mac') mac: string) {
    return this.appleTvService.powerOff(mac);
  }

  @Post('power-on')
  async powerOn(@Body('mac') mac: string) {
    return this.appleTvService.powerOn(mac);
  }

  @Post('sleep')
  async sleep(@Body('mac') mac: string) {
    return this.appleTvService.sleep(mac);
  }

  @Get('scan')
  async scan() {
    return this.appleTvService.scan();
  }
}
